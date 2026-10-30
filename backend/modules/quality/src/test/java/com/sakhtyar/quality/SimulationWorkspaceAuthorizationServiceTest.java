package com.sakhtyar.quality;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class SimulationWorkspaceAuthorizationServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SimulationWorkspaceAuthorizationService service = new SimulationWorkspaceAuthorizationService(jdbc);
    private final UUID workspace = UUID.randomUUID();

    private Authentication principal(String username, boolean allowed) {
        return new UsernamePasswordAuthenticationToken(username, "unused",
                allowed ? List.of(new SimpleGrantedAuthority("USER_MANAGE")) : List.of());
    }

    @Test void permitsVerifiedOwner() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(workspace), eq("owner"))).thenReturn(true);
        assertEquals("owner", service.requireActiveOwner(workspace, principal("owner", true)));
    }

    @Test void deniesOtherWorkspaceOwnerWithoutRevealingExistence() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(workspace), eq("outsider"))).thenReturn(false);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.requireActiveOwner(workspace, principal("outsider", true)));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test void deniesInsufficientAuthorityBeforeDatabaseQuery() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.requireActiveOwner(workspace, principal("owner", false)));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verifyNoInteractions(jdbc);
    }

    @Test void deniesAbsentIdentityAndNullWorkspace() {
        assertThrows(ResponseStatusException.class, () -> service.requireActiveOwner(workspace, null));
        assertThrows(ResponseStatusException.class, () -> service.requireActiveOwner(null, principal("owner", true)));
        verifyNoInteractions(jdbc);
    }

    @Test void deniesMissingOrArchivedWorkspace() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(workspace), eq("owner"))).thenReturn(false);
        assertThrows(ResponseStatusException.class,
                () -> service.requireActiveOwner(workspace, principal("owner", true)));
    }
}
