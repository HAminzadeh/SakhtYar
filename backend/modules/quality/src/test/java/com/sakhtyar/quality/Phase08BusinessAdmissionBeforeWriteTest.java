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
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class Phase08BusinessAdmissionBeforeWriteTest {
    @Test void blockedAdmissionNeverCreatesBusinessRunOrTesterIdentity() {
        var db=mock(JdbcTemplate.class);
        var fixtures=mock(SimulationCoreFixtureService.class);
        var tester=mock(E2ETesterIdentityService.class);
        var authz=mock(SimulationWorkspaceAuthorizationService.class);
        var controller=new SimulationBusinessE2EController(db,fixtures,mock(ObjectMapper.class),
            mock(SimulationScenarioChecks.class),tester,new ScenarioExecutorRegistry(List.of()),
            new E2EEvidenceCollector(),authz);
        var admin=new UsernamePasswordAuthenticationToken("admin",null,
            List.of(new SimpleGrantedAuthority("USER_MANAGE")));
        when(fixtures.readiness()).thenReturn(Map.of("coreFixturePermitted",false));
        when(db.queryForMap(anyString())).thenReturn(Map.of("db_current_user","postgres",
            "db_session_user","postgres","row_security","on"));
        UUID workspace=UUID.randomUUID();
        var error=assertThrows(ResponseStatusException.class,()->controller.start(
            new SimulationBusinessE2EController.StartRequest(workspace,"safe_request_123",List.of("E2E-14")),admin));
        assertEquals(HttpStatus.CONFLICT,error.getStatusCode());
        verify(authz).requireActiveOwner(workspace,admin);
        verify(db).queryForMap(anyString());
        verifyNoMoreInteractions(db);
        verifyNoInteractions(tester);
    }
}
