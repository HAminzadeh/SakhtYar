package com.sakhtyar.quality;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * P02: read-only PostgreSQL catalog evidence for the EXISTING shared database.
 * This is not an admission to mutate TEST rows or a proof of full REAL/TEST isolation.
 * It intentionally never reads business row contents, creates roles or changes RLS.
 */
@RestController
public final class Phase02SharedDatabaseAuditController {
    private static final Set<String> CORE = Set.of(
            "construction_case", "property", "property_owner", "case_document");
    private static final Set<String> EXTERNAL = Set.of("notification_outbox", "crawl_job");
    private static final String CATALOG_SQL = """
            SELECT c.relname AS table_name,
                   EXISTS(SELECT 1 FROM pg_catalog.pg_attribute a
                          WHERE a.attrelid = c.oid AND a.attnum > 0
                            AND NOT a.attisdropped AND a.attname = 'data_scope') AS scope_tagged,
                   EXISTS(SELECT 1 FROM pg_catalog.pg_attribute a
                          WHERE a.attrelid = c.oid AND a.attnum > 0
                            AND NOT a.attisdropped AND a.attname = 'test_workspace_id') AS workspace_tagged,
                   c.relrowsecurity AS rls_enabled, c.relforcerowsecurity AS rls_forced,
                   EXISTS(SELECT 1 FROM pg_catalog.pg_policies p
                          WHERE p.schemaname = 'public' AND p.tablename = c.relname
                            AND p.policyname = 'sakhtyar_core_scope_policy') AS core_scope_policy,
                   EXISTS(SELECT 1 FROM pg_catalog.pg_trigger t
                          WHERE t.tgrelid = c.oid AND t.tgname = 'trg_sakhtyar_core_scope'
                            AND NOT t.tgisinternal AND t.tgenabled <> 'D') AS core_scope_trigger,
                   EXISTS(SELECT 1 FROM pg_catalog.pg_trigger t
                          WHERE t.tgrelid = c.oid AND t.tgname = 'trg_sakhtyar_test_egress'
                            AND NOT t.tgisinternal AND t.tgenabled <> 'D') AS egress_trigger
            FROM pg_catalog.pg_class c
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public' AND c.relkind IN ('r','p')
              AND (c.relname IN ('construction_case','property','property_owner',
                                 'case_document','notification_outbox','crawl_job')
                   OR EXISTS(SELECT 1 FROM pg_catalog.pg_attribute a
                             WHERE a.attrelid = c.oid AND a.attnum > 0
                               AND NOT a.attisdropped AND a.attname = 'data_scope'))
            ORDER BY c.relname
            """;
    private static final String PRINCIPAL_SQL = """
            SELECT (current_user = session_user) AS principal_matches_session,
                   r.rolsuper AS superuser, r.rolbypassrls AS bypass_rls,
                   r.rolcreaterole AS create_role, r.rolcreatedb AS create_db,
                   (current_setting('row_security') = 'on') AS row_security_on
            FROM pg_catalog.pg_roles r WHERE r.rolname = current_user
            """;
    private final JdbcTemplate jdbc;

    public Phase02SharedDatabaseAuditController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/v1/admin/quality/database-isolation")
    @Transactional(readOnly = true)
    public Map<String, Object> inspect(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities() == null
                || authentication.getAuthorities().stream().noneMatch(
                        a -> "USER_MANAGE".equals(a.getAuthority()))
                || authentication.getAuthorities().stream().anyMatch(
                        a -> "ROLE_TESTER".equals(a.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Database security inspection requires an administrator");
        }
        try {
            // Only static SELECT queries against pg_catalog; no business-data SELECT.
            List<Map<String, Object>> tables = jdbc.queryForList(CATALOG_SQL);
            Map<String, Object> principal = jdbc.queryForMap(PRINCIPAL_SQL);
            Set<String> observed = new HashSet<>();
            List<String> coreGaps = new ArrayList<>();
            List<String> egressGaps = new ArrayList<>();
            List<String> scopeWithoutForcedRls = new ArrayList<>();
            int tagged = 0;
            for (Map<String, Object> table : tables) {
                String name = String.valueOf(table.get("table_name"));
                observed.add(name);
                boolean hasScope = truth(table, "scope_tagged");
                boolean hasWorkspace = truth(table, "workspace_tagged");
                boolean rls = truth(table, "rls_enabled") && truth(table, "rls_forced");
                if (hasScope) {
                    tagged++;
                    if (!rls) scopeWithoutForcedRls.add(name);
                }
                if (CORE.contains(name) && (!hasScope || !hasWorkspace || !rls
                        || !truth(table, "core_scope_policy")
                        || !truth(table, "core_scope_trigger"))) {
                    coreGaps.add(name);
                }
                if (EXTERNAL.contains(name) && (!hasScope || !truth(table, "egress_trigger"))) {
                    egressGaps.add(name);
                }
            }
            for (String name : CORE) if (!observed.contains(name)) coreGaps.add(name);
            for (String name : EXTERNAL) if (!observed.contains(name)) egressGaps.add(name);
            coreGaps.sort(String::compareTo);
            egressGaps.sort(String::compareTo);
            scopeWithoutForcedRls.sort(String::compareTo);
            boolean restrictedRole = !truth(principal, "superuser")
                    && !truth(principal, "bypass_rls")
                    && !truth(principal, "create_role")
                    && !truth(principal, "create_db")
                    && truth(principal, "row_security_on")
                    && truth(principal, "principal_matches_session");
            // This audit deliberately NEVER authorizes business writes or claims global isolation.
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("state", "OBSERVATION_ONLY");
            result.put("databaseTopology", "SINGLE_SHARED_POSTGRESQL");
            result.put("readOnly", true);
            result.put("scopeTaggedTableCount", tagged);
            result.put("coreRlsStructurePresent", coreGaps.isEmpty());
            result.put("coreRlsGaps", List.copyOf(coreGaps));
            result.put("externalQueueGuardStructurePresent", egressGaps.isEmpty());
            result.put("externalQueueGuardGaps", List.copyOf(egressGaps));
            result.put("scopeTaggedTablesWithoutForcedRls", List.copyOf(scopeWithoutForcedRls));
            result.put("runtimePrincipalRestricted", restrictedRole);
            result.put("restrictedWorkspaceLoginVerified", false);
            result.put("crossWorkspaceNegativeTestsVerified", false);
            result.put("globalIsolationVerified", false);
            result.put("businessE2eWritesAuthorized", false);
            result.put("doesNotReadBusinessRows", true);
            result.put("requiresDatabaseRuntimeAndNegativeProof", true);
            return result;
        } catch (RuntimeException ex) {
            // Do not expose SQL messages, DB principal names or connection strings.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Database isolation catalog inspection unavailable");
        }
    }

    private static boolean truth(Map<String, Object> row, String field) {
        return Boolean.TRUE.equals(row.get(field));
    }
}
