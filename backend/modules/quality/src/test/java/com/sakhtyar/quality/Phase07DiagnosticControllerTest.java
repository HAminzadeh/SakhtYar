package com.sakhtyar.quality;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Phase07DiagnosticControllerTest {
    private final SimulationScenarioChecks checks=mock(SimulationScenarioChecks.class);
    private final SimulationWorkspaceAuthorizationService owners=mock(SimulationWorkspaceAuthorizationService.class);
    private final Phase07DiagnosticCenterController controller=new Phase07DiagnosticCenterController(
        checks,owners,List.of(new Phase07E2E01Executor(),new Phase07E2E02Executor(),new Phase07E2E03Executor(),
        new Phase07E2E04Executor(),new Phase07E2E05Executor(),new Phase07E2E06Executor(),
        new Phase07E2E07Executor(),new Phase07E2E08Executor(),new Phase07E2E09Executor(),
        new Phase07E2E10Executor(),new Phase07E2E11Executor(),new Phase07E2E12Executor(),
        new Phase07E2E13Executor(),new Phase07E2E14Executor(),new Phase07E2E15Executor(),
        new Phase07E2E16Executor(),new Phase07E2E17Executor(),new Phase07E2E18Executor(),
        new Phase07E2E19Executor(),new Phase07E2E20Executor(),new Phase07E2E21Executor(),
        new Phase07E2E22Executor(),new Phase07E2E23Executor(),new Phase07E2E24Executor()));

    private UsernamePasswordAuthenticationToken admin() {
        return new UsernamePasswordAuthenticationToken("admin", null,
            List.of(new SimpleGrantedAuthority("USER_MANAGE")));
    }

    @Test void readOnlyDiagnosticsCannotBecomeBusinessPass() {
        UUID workspace=UUID.randomUUID();
        when(checks.check(any(),anyInt()))
            .thenAnswer(i->new SimulationScenarioChecks.Outcome("PASSED","read-only",Map.of("kind","METADATA_ONLY")));
        when(checks.domainIntegrity(any()))
            .thenReturn(new SimulationScenarioChecks.Outcome("BLOCKED","restricted TEST login not proven",Map.of()));
        Map<String,Object> result=controller.run(
            new Phase07DiagnosticCenterController.Request(workspace,List.of("E2E-01")),admin());
        assertEquals("COMPLETED",result.get("state"));
        assertEquals(24,result.get("diagnosticExecutorCount"));
        assertEquals(0,result.get("businessScenarioPassedCount"));
        assertEquals(false,result.get("businessWorkflowsExecuted"));
        assertEquals(false,result.get("businessDatabaseWrites"));
        assertEquals(1,result.get("scenario_count"));
        verify(owners).requireActiveOwner(eq(workspace),any());
        verify(checks,times(4)).check(any(),anyInt());
    }

    @Test void missingAdminAndForeignWorkspaceFailBeforeProbes() {
        UUID workspace=UUID.randomUUID();
        var req=new Phase07DiagnosticCenterController.Request(workspace,List.of("E2E-01"));
        assertThrows(ResponseStatusException.class,()->controller.run(req,null));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,"Not found"))
            .when(owners).requireActiveOwner(eq(workspace),any());
        assertThrows(ResponseStatusException.class,()->controller.run(req,admin()));
        verifyNoInteractions(checks);
    }
}
