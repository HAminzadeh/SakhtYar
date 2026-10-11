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

    public CaseService(CaseRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CaseResponse> list() {
        return repository.findAllByOrderByUpdatedAtDesc()
                .stream()
                .map(CaseResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CaseResponse get(UUID id) {
        return CaseResponse.from(require(id));
    }

    @Transactional
    public CaseResponse create(
            UpsertCaseRequest request,
            Authentication authentication
    ) {
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

    private CaseEntity require(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Case not found."));
    }
}
