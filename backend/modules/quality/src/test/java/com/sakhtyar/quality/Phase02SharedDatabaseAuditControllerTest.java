package com.sakhtyar.quality;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class Phase02SharedDatabaseAuditControllerTest {
    private static Authentication auth(String... permissions) {
        return new UsernamePasswordAuthenticationToken("principal", "N/A",
                java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList());
    }
    private static Map<String, Object> row(String name, boolean rls, boolean policy,
            boolean trigger, boolean egress) {
        Map<String, Object> item = new HashMap<>();
        item.put("table_name", name);
        item.put("scope_tagged", true);
        item.put("workspace_tagged", true);
        item.put("rls_enabled", rls);
        item.put("rls_forced", rls);
        item.put("core_scope_policy", policy);
        item.put("core_scope_trigger", trigger);
        item.put("egress_trigger", egress);
        return item;
    }
    private static Map<String, Object> principal() {
        return Map.of("superuser", false, "bypass_rls", false, "create_role", false,
                "create_db", false, "row_security_on", true, "principal_matches_session", true);
    }

    @Test void anonymousCannotInspectCatalogOrReachJdbc() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        var controller = new Phase02SharedDatabaseAuditController(db);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.inspect(null)).getStatusCode());
        verifyNoInteractions(db);
    }
    @Test void qualityTesterCannotInspectCatalogEvenWithQualityView() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        var controller = new Phase02SharedDatabaseAuditController(db);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.inspect(auth("ROLE_TESTER", "QUALITY_VIEW"))).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.inspect(auth("ROLE_TESTER", "USER_MANAGE"))).getStatusCode());
        verifyNoInteractions(db);
    }
    @Test void coreAndEgressStructureCannotBeConfusedWithFullIsolationProof() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForList(anyString())).thenReturn(List.of(
                row("construction_case", true, true, true, false),
                row("property", true, true, true, false),
                row("property_owner", true, true, true, false),
                row("case_document", true, true, true, false),
                row("notification_outbox", false, false, false, true),
                row("crawl_job", false, false, false, true),
                row("cost_estimate", false, false, false, false)));
        when(db.queryForMap(anyString())).thenReturn(principal());
        var result = new Phase02SharedDatabaseAuditController(db).inspect(auth("USER_MANAGE"));
        assertEquals("OBSERVATION_ONLY", result.get("state"));
        assertEquals(true, result.get("coreRlsStructurePresent"));
        assertEquals(true, result.get("externalQueueGuardStructurePresent"));
        assertEquals(true, result.get("runtimePrincipalRestricted"));
        assertEquals(false, result.get("globalIsolationVerified"));
        assertEquals(false, result.get("businessE2eWritesAuthorized"));
        assertTrue(((List<?>) result.get("scopeTaggedTablesWithoutForcedRls"))
                .contains("cost_estimate"));
        verify(db, times(1)).queryForList(anyString());
        verify(db, times(1)).queryForMap(anyString());
    }
    @Test void missingCoreTableIsReportedNotPassed() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForList(anyString())).thenReturn(List.of(row("property", true, true, true, false)));
        when(db.queryForMap(anyString())).thenReturn(principal());
        var result = new Phase02SharedDatabaseAuditController(db).inspect(auth("USER_MANAGE"));
        assertEquals(false, result.get("coreRlsStructurePresent"));
        assertTrue(((List<?>) result.get("coreRlsGaps")).contains("construction_case"));
    }
    @Test void jdbcFailureIsFailClosedAndDoesNotLeakSQLException() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForList(anyString())).thenThrow(new IllegalStateException("secret db-password"));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> new Phase02SharedDatabaseAuditController(db).inspect(auth("USER_MANAGE")));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        assertFalse(error.getReason().contains("db-password"));
    }
}
