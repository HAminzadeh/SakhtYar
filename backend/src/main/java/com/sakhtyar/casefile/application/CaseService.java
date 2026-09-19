package com.sakhtyar.casefile.application;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.api.CaseDtos.CaseResponse;
import com.sakhtyar.casefile.api.CaseDtos.UpsertCaseRequest;
import com.sakhtyar.casefile.domain.CaseEntity;
import com.sakhtyar.casefile.domain.CaseRepository;
import java.time.Instant;
import java.util.List;
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

        CaseEntity entity = new CaseEntity(
                UUID.randomUUID(),
                request.title().trim(),
                request.status(),
                request.description(),
                request.city(),
                request.district(),
                request.address(),
                request.landAreaM2(),
                authentication.getName(),
                now,
                now
        );

        repository.save(entity);

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
                request.landAreaM2()
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
