package com.sakhtyar.quality;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Server-derived, fail-closed owner authorization for T10 simulation workspaces.
 * This service never trusts a client-supplied username or a PostgreSQL session GUC.
 * ADMIN/USER_MANAGE does not implicitly grant cross-workspace history access.
 * Call at the history API/service boundary before operating on a workspace.
 */
@Service
public final class SimulationWorkspaceAuthorizationService {
    private final JdbcTemplate jdbc;

    public SimulationWorkspaceAuthorizationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String requireActiveOwner(UUID workspaceId, Authentication authentication) {
        if (workspaceId == null || authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()
                || authentication.getAuthorities() == null || authentication.getAuthorities().stream()
                    .noneMatch(a -> "USER_MANAGE".equals(a.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Workspace access denied");
        }
        String principal = authentication.getName();
        Boolean authorized = jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1 FROM public.simulation_workspace
                WHERE id = ? AND state = 'ACTIVE'
                  AND lower(owner_username) = lower(?)
            )
            """, Boolean.class, workspaceId, principal);
        if (!Boolean.TRUE.equals(authorized)) {
            // Do not disclose existence or owner information.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found");
        }
        return principal;
    }
}
