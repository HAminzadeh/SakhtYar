package com.sakhtyar.quality;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Strict, side-effect-free pre-write admission contract for business E2E.
 *
 * Fixture readiness and application JDBC identity are observations, NOT proof of
 * a restricted physical TEST login or an application's end-to-end isolation.
 * A missing, false, malformed or stale proof must never authorize a DB write.
 * This class opens no connection, provisions no role and does not execute SQL.
 */
public final class Phase08SafetyAdmission {
    private Phase08SafetyAdmission() {}

    public record Verdict(boolean authorized, List<String> blockers, Map<String,Object> evidence) {
        public Verdict {
            blockers=List.copyOf(blockers);
            evidence=Map.copyOf(evidence);
            if(authorized && !blockers.isEmpty()) throw new IllegalArgumentException("Conflicting admission proof");
        }
        public Map<String,Object> report() {
            Map<String,Object> out=new LinkedHashMap<>(evidence);
            out.put("authorizedBusinessWrites",authorized);
            out.put("state",authorized?"PROOF_REVIEW_REQUIRED":"BLOCKED");
            out.put("blockers",blockers);
            out.put("scope","BUSINESS_E2E_PRE_WRITE_ADMISSION_NOT_BUSINESS_EVIDENCE");
            out.put("databaseWritesExecuted",false);
            out.put("externalCallsExecuted",0);
            return out;
        }
    }

    public static Verdict assess(UUID workspaceId, Map<String,Object> coreReadiness,
            Map<String,Object> jdbcIdentity, boolean devSeedEnabled, String sourceCommit) {
        Map<String,Object> ready=coreReadiness==null?Map.of():coreReadiness;
        Map<String,Object> identity=jdbcIdentity==null?Map.of():jdbcIdentity;
        List<String> blockers=new ArrayList<>();
        String expected=workspaceId==null?"":("sakhtyar_tws_"+workspaceId.toString().replace("-",""));
        String current=String.valueOf(identity.getOrDefault("db_current_user",""));
        String session=String.valueOf(identity.getOrDefault("db_session_user",""));
        String security=String.valueOf(identity.getOrDefault("row_security",""));
        boolean physicalLogin=workspaceId!=null && expected.equals(current) && expected.equals(session) &&
            "on".equalsIgnoreCase(security);
        boolean fixturePermitted=truth(ready,"coreFixturePermitted");
        boolean loginRoleSafe=truth(ready,"runtimeDbRoleNonBypass");
        boolean crossWorkspace= "CORE_READ_PROBE_OBSERVED".equals(ready.get("physicalLoginIsolationEvidence"));
        boolean fullApp=truth(ready,"fullApplicationIsolationVerified");
        boolean outbound=truth(ready,"externalSideEffectsGloballyGuarded");
        boolean catalog=truth(ready,"businessE2eCatalogEnabled");
        boolean cleanup=truth(ready,"testDataCleanupScoped");
        boolean artifacts=truth(ready,"verifiedEvidenceStoreReady");
        boolean validCommit=sourceCommit!=null && sourceCommit.matches("(?i)[0-9a-f]{40}");
        if(workspaceId==null) blockers.add("WORKSPACE_MISSING");
        if(!fixturePermitted) blockers.add("CORE_FIXTURE_GATE_NOT_VERIFIED");
        if(!physicalLogin) blockers.add("PHYSICAL_RESTRICTED_TEST_LOGIN_NOT_VERIFIED");
        if(!loginRoleSafe) blockers.add("RESTRICTED_DB_PRIVILEGES_NOT_VERIFIED");
        if(!crossWorkspace) blockers.add("CROSS_WORKSPACE_REAL_INVISIBILITY_NOT_PROVEN");
        if(!fullApp) blockers.add("FULL_APPLICATION_SCOPE_NOT_VERIFIED");
        if(!outbound) blockers.add("EXTERNAL_EFFECTS_NOT_GLOBALLY_GUARDED");
        if(!catalog) blockers.add("FULL_BUSINESS_EXECUTOR_CATALOG_NOT_READY");
        if(!cleanup) blockers.add("RUN_SCOPED_CLEANUP_NOT_VERIFIED");
        if(!artifacts) blockers.add("EVIDENCE_STORE_NOT_VERIFIED");
        if(devSeedEnabled) blockers.add("DEVELOPMENT_SEED_MUST_BE_DISABLED");
        if(!validCommit) blockers.add("EMBEDDED_SOURCE_COMMIT_UNVERIFIED");
        Map<String,Object> evidence=new LinkedHashMap<>();
        evidence.put("workspaceId",workspaceId==null?"UNSET":workspaceId.toString());
        evidence.put("coreFixturePermitted",fixturePermitted);
        evidence.put("physicalRestrictedLogin",physicalLogin);
        evidence.put("restrictedRolePrivilegesVerified",loginRoleSafe);
        evidence.put("crossWorkspaceRealInvisibilityProven",crossWorkspace);
        evidence.put("fullApplicationIsolationVerified",fullApp);
        evidence.put("externalEffectsGloballyGuarded",outbound);
        evidence.put("fullBusinessCatalogEnabled",catalog);
        evidence.put("runScopedCleanupVerified",cleanup);
        evidence.put("evidenceStoreVerified",artifacts);
        evidence.put("devSeedDisabled",!devSeedEnabled);
        evidence.put("sourceCommitVerified",validCommit);
        evidence.put("sourceCommit",validCommit?sourceCommit:"UNVERIFIED");
        evidence.put("loginIdentityKind",physicalLogin?"PHYSICAL_WORKSPACE_RESTRICTED_TEST_LOGIN":"APP_ROLE_OR_UNVERIFIED");
        evidence.put("requiresRealRowsOrSideEffects",false);
        return new Verdict(blockers.isEmpty(),blockers,evidence);
    }

    private static boolean truth(Map<String,Object> source, String key) {
        return Boolean.TRUE.equals(source.get(key));
    }
}
