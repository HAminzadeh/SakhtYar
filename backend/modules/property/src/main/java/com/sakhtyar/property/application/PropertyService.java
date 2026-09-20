package com.sakhtyar.property.application;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.domain.CaseEntity;
import com.sakhtyar.casefile.domain.CaseRepository;
import com.sakhtyar.property.api.PropertyDtos.PropertyResponse;
import com.sakhtyar.property.api.PropertyDtos.UpsertPropertyRequest;
import com.sakhtyar.property.domain.PropertyEntity;
import com.sakhtyar.property.domain.PropertyRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PropertyService {

    private final PropertyRepository repository;
    private final CaseRepository caseRepository;
    private final AuditService auditService;

    public PropertyService(
            PropertyRepository repository,
            CaseRepository caseRepository,
            AuditService auditService
    ) {
        this.repository = repository;
        this.caseRepository = caseRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PropertyResponse get(UUID caseId) {
        requireCase(caseId);

        PropertyEntity entity = repository.findByCaseId(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Property not found for case."
                ));

        return PropertyResponse.from(entity);
    }

    @Transactional
    public PropertyResponse upsert(UUID caseId, UpsertPropertyRequest request) {
        CaseEntity caseEntity = requireCase(caseId);

        PropertyEntity entity = repository.findByCaseId(caseId).orElse(null);
        boolean created = entity == null;

        if (created) {
            Instant now = Instant.now();
            entity = new PropertyEntity(
                    UUID.randomUUID(),
                    caseId,
                    clean(request.province()),
                    clean(request.city()),
                    clean(request.district()),
                    clean(request.neighborhood()),
                    clean(request.address()),
                    request.landAreaM2(),
                    clean(request.registryMainNo()),
                    clean(request.registrySubNo()),
                    clean(request.registrySection()),
                    clean(request.postalCode()),
                    request.latitude(),
                    request.longitude(),
                    now,
                    now
            );
        } else {
            entity.update(
                    clean(request.province()),
                    clean(request.city()),
                    clean(request.district()),
                    clean(request.neighborhood()),
                    clean(request.address()),
                    request.landAreaM2(),
                    clean(request.registryMainNo()),
                    clean(request.registrySubNo()),
                    clean(request.registrySection()),
                    clean(request.postalCode()),
                    request.latitude(),
                    request.longitude()
            );
        }

        repository.save(entity);

        caseEntity.syncPropertySnapshot(
                entity.getCity(),
                entity.getDistrict(),
                entity.getAddress(),
                entity.getLandAreaM2()
        );

        auditService.record(
                "PROPERTY",
                entity.getId(),
                created ? "PROPERTY_CREATED" : "PROPERTY_UPDATED",
                Map.of(
                        "caseId", caseId.toString(),
                        "propertyId", entity.getId().toString()
                )
        );

        return PropertyResponse.from(entity);
    }

    private CaseEntity requireCase(UUID caseId) {
        return caseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Case not found."
                ));
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
