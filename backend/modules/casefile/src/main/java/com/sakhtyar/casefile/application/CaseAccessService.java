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
        if (item == null || !"REAL".equals(item.getDataScope())) return false;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null || auth.getAuthorities() == null) return false;
        if (auth.getAuthorities().stream().anyMatch(x -> "ROLE_TESTER".equals(x.getAuthority()))) return false;
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

    /** Membership currently grants read access only; grant/revoke/write require creator or admin. */
    public void requireWrite(CaseEntity item) {
        if (item == null || !"REAL".equals(item.getDataScope()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found.");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getAuthorities() == null || auth.getName() == null ||
            auth.getAuthorities().stream().anyMatch(x -> "ROLE_TESTER".equals(x.getAuthority())))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found.");
        boolean admin = auth.getAuthorities().stream().anyMatch(x -> "ROLE_ADMIN".equals(x.getAuthority()));
        if (!admin && !item.getCreatedBy().equalsIgnoreCase(auth.getName()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found.");
    }

    public void requireWrite(UUID id) {
        CaseEntity item = cases.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found."));
        requireWrite(item);
    }

    public void requireRead(UUID id) {
        CaseEntity item = cases.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found."));
        requireRead(item);
    }
}
