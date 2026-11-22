package com.sakhtyar.quality;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class Phase08SafetyReadinessControllerTest {
    @Test void nonAdminCannotObserveWorkspaceOrQueryDatabase() {
        var db=mock(JdbcTemplate.class);
        var fixture=mock(SimulationCoreFixtureService.class);
        var authz=mock(SimulationWorkspaceAuthorizationService.class);
        var ctrl=new Phase08SafetyReadinessController(db,fixture,authz);
        var nonAdmin=new UsernamePasswordAuthenticationToken("owner",null,List.of());
        var ex=assertThrows(ResponseStatusException.class,()->ctrl.readiness(UUID.randomUUID(),nonAdmin));
        assertEquals(HttpStatus.FORBIDDEN,ex.getStatusCode());
        verifyNoInteractions(db,fixture,authz);
    }
    @Test void authorizedAdminReceivesOnlyReadinessAndNoDbWrites() {
        var db=mock(JdbcTemplate.class);
        var fixture=mock(SimulationCoreFixtureService.class);
        var authz=mock(SimulationWorkspaceAuthorizationService.class);
        var ctrl=new Phase08SafetyReadinessController(db,fixture,authz);
        UUID workspace=UUID.randomUUID();
        var admin=new UsernamePasswordAuthenticationToken("admin",null,List.of(new SimpleGrantedAuthority("USER_MANAGE")));
        when(fixture.readiness()).thenReturn(Map.of("coreFixturePermitted",false));
        when(db.queryForMap(anyString())).thenReturn(Map.of("db_current_user","postgres","db_session_user","postgres","row_security","on"));
        var report=ctrl.readiness(workspace,admin);
        assertEquals("BLOCKED",report.get("state"));
        assertEquals("READ_ONLY_PHASE08_SECURITY_READINESS",report.get("mode"));
        assertEquals(7,report.get("targetCount"));
        assertEquals(false,report.get("databaseWritesExecuted"));
        verify(authz).requireActiveOwner(workspace,admin);
        verify(db).queryForMap(anyString());
        verify(db,never()).update(anyString());
        verifyNoMoreInteractions(db);
    }
}
