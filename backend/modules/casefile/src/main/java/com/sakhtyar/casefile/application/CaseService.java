package com.sakhtyar.casefile.application;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.api.CaseDtos.CaseResponse;
import com.sakhtyar.casefile.api.CaseDtos.UpsertCaseRequest;
import com.sakhtyar.casefile.domain.CaseEntity;
import com.sakhtyar.casefile.domain.CaseRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseService {

    private final CaseRepository repository;
    private final AuditService auditService;
    private final CaseAccessService access;

    public CaseService(CaseRepository repository, AuditService auditService, CaseAccessService access) {
        this.repository = repository;
        this.auditService = auditService;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<CaseResponse> list() {
        assertRealBusinessRequest();
        return repository.findAllByOrderByUpdatedAtDesc()
                .stream()
                .filter(row -> "REAL".equals(row.getDataScope()))
                .filter(access::canRead)
                .map(CaseResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CaseResponse get(UUID id) {
        assertRealBusinessRequest();
        return CaseResponse.from(require(id));
    }

    @Transactional
    public CaseResponse create(
            UpsertCaseRequest request,
            Authentication authentication
    ) {
        assertRealBusinessRequest();
        Instant now = Instant.now();

        UUID caseId = UUID.randomUUID();

        String projectCode =
                "SYF-" +
                caseId.toString()
                        .replace("-", "")
                        .toUpperCase(Locale.ROOT);

        CaseEntity entity = new CaseEntity(
                caseId,
                projectCode,
                request.title().trim(),
                request.status(),
                request.description(),
                request.city(),
                request.district(),
                request.address(),
                request.landAreaM2(),
                request.coverImageUrl(),
                authentication.getName(),
                now,
                now
        );

        // Flush the business row before writing the REQUIRES_NEW audit event.
        // This prevents CASE_CREATED audit records for inserts that later fail
        // because of a database constraint.
        repository.saveAndFlush(entity);

        auditService.record(
                "CASE",
                entity.getId(),
                "CASE_CREATED",
                Map.of(
                        "title", entity.getTitle(),
                        "status", entity.getStatus().name()
                )
        );

        return CaseResponse.from(entity);
    }

    @Transactional
    public CaseResponse update(UUID id, UpsertCaseRequest request) {
        assertRealBusinessRequest();
        CaseEntity entity = require(id);

        entity.update(
                request.title().trim(),
                request.status(),
                request.description(),
                request.city(),
                request.district(),
                request.address(),
                request.landAreaM2(),
                request.coverImageUrl()
        );

        auditService.record(
                "CASE",
                entity.getId(),
                "CASE_UPDATED",
                Map.of(
                        "title", entity.getTitle(),
                        "status", entity.getStatus().name()
                )
        );

        return CaseResponse.from(entity);
    }

    /** The ordinary case APIs are REAL-only until a restricted TEST service path is verified. */
    private static void assertRealBusinessRequest() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_TESTER".equals(a.getAuthority()))) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "TESTER must use an isolated TEST business executor, not REAL case APIs");
        }
    }

    private CaseEntity require(UUID id) {
        CaseEntity result = repository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Case not found."));
        if (!"REAL".equals(result.getDataScope())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Case not found.");
        }
        access.requireRead(result);
        return result;
    }
}
