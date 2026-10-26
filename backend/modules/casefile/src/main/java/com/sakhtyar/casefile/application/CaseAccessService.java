package com.sakhtyar.casefile.application;

import com.sakhtyar.casefile.domain.CaseEntity;
import com.sakhtyar.casefile.domain.CaseRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Server-side case authorization. Scope is ADMIN, creator, or an explicitly
 * active case membership; roles with CASE_READ alone do not confer access to
 * every project. Do not call this with an untrusted username parameter.
 */
@Service
public class CaseAccessService {
    private final CaseRepository cases;
    private final JdbcTemplate jdbc;

    public CaseAccessService(CaseRepository cases, JdbcTemplate jdbc) {
        this.cases = cases;
        this.jdbc = jdbc;
    }

    public boolean canRead(CaseEntity item) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) return false;
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(x -> "ROLE_ADMIN".equals(x.getAuthority()));
        if (isAdmin) return true;
        if (item.getCreatedBy().equalsIgnoreCase(auth.getName())) return true;
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists (
                select 1 from construction_case_access m
                where m.case_id = ? and lower(m.username) = lower(?) and m.active = true
            )
            """, Boolean.class, item.getId(), auth.getName()));
    }

    public void requireRead(CaseEntity item) {
        if (!canRead(item)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found.");
    }

    public void requireRead(UUID id) {
        CaseEntity item = cases.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found."));
        requireRead(item);
    }
}
