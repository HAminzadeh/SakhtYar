package com.sakhtyar.quality;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Phase09PreflightSessionControllerTest {
    @Test void lifecycleIsReadOnlyAndBoundToWorkspaceOwner() {
        var authorization=mock(SimulationWorkspaceAuthorizationService.class);
        var readiness=mock(Phase08SafetyReadinessController.class);
        var env=new MockEnvironment();env.setActiveProfiles("development");
        var ctrl=new Phase09PreflightSessionController(authorization,readiness,new Phase09RuntimePolicy(env));
        var admin=new UsernamePasswordAuthenticationToken("owner",null,
            List.of(new SimpleGrantedAuthority("USER_MANAGE")));
        UUID workspace=UUID.randomUUID();
        when(readiness.readiness(workspace,admin)).thenReturn(Map.of("state","BLOCKED","blockers",List.of("CORE_FIXTURE_GATE_NOT_VERIFIED")));
        var session=ctrl.open(new Phase09PreflightSessionController.OpenRequest(workspace),admin);
        assertEquals("READ_ONLY_PREFLIGHT_SESSION_NOT_BUSINESS_E2E",session.get("mode"));
        assertEquals(false,session.get("databaseResourcesCreated"));
        UUID id=UUID.fromString(String.valueOf(session.get("id")));
        assertEquals("INSPECTION_ONLY",ctrl.show(id,admin).get("state"));
        assertEquals("CLOSED",ctrl.close(id,admin).get("state"));
        assertThrows(ResponseStatusException.class,()->ctrl.show(id,admin));
        verify(readiness).readiness(workspace,admin);
        verify(authorization,atLeast(3)).requireActiveOwner(workspace,admin);
    }
    @Test void productionCannotStartSessionEvenWithoutDatabase() {
        var env=new MockEnvironment();env.setActiveProfiles("production");
        var ctrl=new Phase09PreflightSessionController(mock(SimulationWorkspaceAuthorizationService.class),
            mock(Phase08SafetyReadinessController.class),new Phase09RuntimePolicy(env));
        var ex=assertThrows(ResponseStatusException.class,()->ctrl.open(
            new Phase09PreflightSessionController.OpenRequest(UUID.randomUUID()),null));
        assertEquals(HttpStatus.FORBIDDEN,ex.getStatusCode());
    }
}
