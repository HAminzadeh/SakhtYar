package com.sakhtyar.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class SimulationBusinessE2ESecurityTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final E2ETesterIdentityService tester=mock(E2ETesterIdentityService.class);
    private final SimulationWorkspaceAuthorizationService authorization=mock(SimulationWorkspaceAuthorizationService.class);
    private final SimulationBusinessE2EController controller=new SimulationBusinessE2EController(jdbc,
        mock(SimulationCoreFixtureService.class),mock(ObjectMapper.class),
        mock(SimulationScenarioChecks.class),tester,new ScenarioExecutorRegistry(List.of()),
        new E2EEvidenceCollector(),authorization);

    @Test void qualityTesterCannotStartAdminBusinessRun() {
        Authentication nonAdmin=new UsernamePasswordAuthenticationToken("tester",null,
            List.of(new SimpleGrantedAuthority("ROLE_TESTER"),new SimpleGrantedAuthority("QUALITY_RUN_SAFE")));
        assertThatThrownBy(() -> controller.start(
            new SimulationBusinessE2EController.StartRequest(UUID.randomUUID(),"safe_key123",List.of("E2E-14")),nonAdmin))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException)e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(jdbc);
    }

    @Test void crossWorkspaceOwnerDenialStopsBeforeAnyBusinessWrite() {
        UUID foreign=UUID.randomUUID();
        Authentication admin=new UsernamePasswordAuthenticationToken("admin",null,
            List.of(new SimpleGrantedAuthority("USER_MANAGE")));
        when(authorization.requireActiveOwner(eq(foreign),eq(admin)))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,"Workspace not found"));
        assertThatThrownBy(() -> controller.start(
            new SimulationBusinessE2EController.StartRequest(foreign,"request_key_123",List.of("E2E-14")),admin))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void privateWorkerClearsSecurityContextEvenWithThreadReuse() throws Exception {
        try {
            var details=User.withUsername("tester").password("encoded").authorities(
                "ROLE_TESTER","QUALITY_VIEW","QUALITY_RUN_SAFE").build();
            when(tester.loadAuthorities()).thenReturn(details);
            var identity=new E2ETesterIdentityService.Identity(UUID.randomUUID(),"tester",false);
            Method run=SimulationBusinessE2EController.class.getDeclaredMethod("execute",UUID.class,
                UUID.class,String.class,E2ETesterIdentityService.Identity.class,String.class,List.class);
            run.setAccessible(true);
            run.invoke(controller,UUID.randomUUID(),UUID.randomUUID(),"admin",identity,"trace",List.of());
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test void startupMarksInFlightRunsInterrupted() {
        controller.onApplicationEvent(null);
        verify(jdbc,times(3)).update(anyString());
    }
}
