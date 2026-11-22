package com.sakhtyar.casefile.application;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.domain.CaseEntity;
import com.sakhtyar.casefile.domain.CaseRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** REAL membership ACL; it intentionally does not provision TEST fixtures or TEST users. */
@Service
public class CaseMembershipService {
    private final CaseRepository cases;
    private final CaseAccessService access;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public CaseMembershipService(CaseRepository cases, CaseAccessService access, JdbcTemplate jdbc, AuditService audit) {
        this.cases = cases;
        this.access = access;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    private CaseEntity ownedCase(UUID id) {
        CaseEntity item = cases.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found."));
        access.requireWrite(item);
        return item;
    }

    private static String actor() {
        var a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a.getName() == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication required");
        return a.getName();
    }

    private static String validateUsername(String username) {
        if (username == null || username.isBlank() || username.length() > 100 ||
            username.contains("/") || username.contains("\\") || username.indexOf('\0') >= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid member username");
        return username.trim();
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> list(UUID caseId) {
        ownedCase(caseId);
        return jdbc.queryForList("""
            select username, active, granted_by, granted_at from construction_case_access
            where case_id=? order by lower(username)
            """, caseId);
    }

    @Transactional
    public void grant(UUID caseId, String username) {
        ownedCase(caseId);
        String candidate = validateUsername(username);
        // Lock parent row to serialize case ACL mutations by concurrent managers.
        jdbc.queryForList("select id from construction_case where id=? for update", UUID.class, caseId);
        List<String> principals=jdbc.queryForList("""
            select username from app_user where lower(username)=lower(?)
            and active=true and status='ACTIVE' and role <> 'TESTER'
            """, String.class, candidate);
        if (principals.size()!=1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active member not found");
        String member = principals.get(0);
        int updated = jdbc.update("""
            update construction_case_access set active=true,granted_by=?,granted_at=now()
            where case_id=? and lower(username)=lower(?)
            """,actor(),caseId,member);
        if(updated==0) jdbc.update("""
            insert into construction_case_access(case_id,username,active,granted_by,granted_at)
            values(?,?,true,?,now())
            """,caseId,member,actor());
        audit.record("CASE",caseId,"CASE_ACCESS_GRANTED",Map.of("member",member));
    }

    @Transactional
    public void revoke(UUID caseId, String username) {
        ownedCase(caseId);
        String member=validateUsername(username);
        jdbc.queryForList("select id from construction_case where id=? for update", UUID.class, caseId);
        int updated=jdbc.update("""
            update construction_case_access set active=false where case_id=? and lower(username)=lower(?) and active=true
            """,caseId,member);
        if(updated==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Active member not found");
        audit.record("CASE",caseId,"CASE_ACCESS_REVOKED",Map.of("member",member));
    }
}
