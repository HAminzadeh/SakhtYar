package com.sakhtyar.owner.application;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.owner.api.OwnerDtos.OwnerResponse;
import com.sakhtyar.owner.api.OwnerDtos.UpsertOwnerRequest;
import com.sakhtyar.owner.domain.OwnerEntity;
import com.sakhtyar.owner.domain.OwnerRepository;
import com.sakhtyar.property.domain.PropertyEntity;
import com.sakhtyar.property.domain.PropertyRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OwnerService {

    private static final int SHARE_SCALE = 12;

    private final OwnerRepository repository;
    private final PropertyRepository propertyRepository;
    private final AuditService auditService;

    public OwnerService(
            OwnerRepository repository,
            PropertyRepository propertyRepository,
            AuditService auditService
    ) {
        this.repository = repository;
        this.propertyRepository = propertyRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<OwnerResponse> list(UUID caseId) {
        PropertyEntity property = requireProperty(caseId);

        return repository.findByPropertyIdOrderByCreatedAtAsc(property.getId())
                .stream()
                .map(OwnerResponse::from)
                .toList();
    }

    @Transactional
    public OwnerResponse create(UUID caseId, UpsertOwnerRequest request) {
        PropertyEntity property = requireProperty(caseId);

        validateShareValues(request);
        validateNationalIdUniqueness(property.getId(), request.nationalId(), null);
        validateTotalOwnership(
                property.getId(),
                null,
                request.ownershipNumerator(),
                request.ownershipDenominator()
        );

        if (request.primaryContact()) {
            clearOtherPrimaryContacts(property.getId(), null);
        }

        Instant now = Instant.now();
        OwnerEntity entity = new OwnerEntity(
                UUID.randomUUID(),
                property.getId(),
                request.firstName().trim(),
                request.lastName().trim(),
                clean(request.nationalId()),
                clean(request.mobile()),
                request.ownershipNumerator(),
                request.ownershipDenominator(),
                request.primaryContact(),
                now,
                now
        );

        repository.save(entity);

        auditService.record(
                "OWNER",
                entity.getId(),
                "OWNER_CREATED",
                Map.of(
                        "caseId", caseId.toString(),
                        "propertyId", property.getId().toString()
                )
        );

        return OwnerResponse.from(entity);
    }

    @Transactional
    public OwnerResponse update(
            UUID caseId,
            UUID ownerId,
            UpsertOwnerRequest request
    ) {
        PropertyEntity property = requireProperty(caseId);
        OwnerEntity entity = requireOwner(property.getId(), ownerId);

        validateShareValues(request);
        validateNationalIdUniqueness(property.getId(), request.nationalId(), ownerId);
        validateTotalOwnership(
                property.getId(),
                ownerId,
                request.ownershipNumerator(),
                request.ownershipDenominator()
        );

        if (request.primaryContact()) {
            clearOtherPrimaryContacts(property.getId(), ownerId);
        }

        entity.update(
                request.firstName().trim(),
                request.lastName().trim(),
                clean(request.nationalId()),
                clean(request.mobile()),
                request.ownershipNumerator(),
                request.ownershipDenominator(),
                request.primaryContact()
        );

        auditService.record(
                "OWNER",
                entity.getId(),
                "OWNER_UPDATED",
                Map.of(
                        "caseId", caseId.toString(),
                        "propertyId", property.getId().toString()
                )
        );

        return OwnerResponse.from(entity);
    }

    @Transactional
    public void delete(UUID caseId, UUID ownerId) {
        PropertyEntity property = requireProperty(caseId);
        OwnerEntity entity = requireOwner(property.getId(), ownerId);

        repository.delete(entity);

        auditService.record(
                "OWNER",
                ownerId,
                "OWNER_DELETED",
                Map.of(
                        "caseId", caseId.toString(),
                        "propertyId", property.getId().toString()
                )
        );
    }

    private PropertyEntity requireProperty(UUID caseId) {
        return propertyRepository.findByCaseId(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Property not found for case. Save property information first."
                ));
    }

    private OwnerEntity requireOwner(UUID propertyId, UUID ownerId) {
        return repository.findByIdAndPropertyId(ownerId, propertyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Owner not found."
                ));
    }

    private void validateShareValues(UpsertOwnerRequest request) {
        if (request.ownershipNumerator() > request.ownershipDenominator()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ownership numerator cannot be greater than denominator."
            );
        }
    }

    private void validateNationalIdUniqueness(
            UUID propertyId,
            String nationalId,
            UUID ownerId
    ) {
        String cleaned = clean(nationalId);
        if (cleaned == null) {
            return;
        }

        boolean duplicate = ownerId == null
                ? repository.existsByPropertyIdAndNationalIdIgnoreCase(propertyId, cleaned)
                : repository.existsByPropertyIdAndNationalIdIgnoreCaseAndIdNot(
                        propertyId,
                        cleaned,
                        ownerId
                );

        if (duplicate) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An owner with this national ID already exists for this property."
            );
        }
    }

    private void validateTotalOwnership(
            UUID propertyId,
            UUID excludedOwnerId,
            int newNumerator,
            int newDenominator
    ) {
        BigDecimal total = share(newNumerator, newDenominator);

        for (OwnerEntity owner :
                repository.findByPropertyIdOrderByCreatedAtAsc(propertyId)) {

            if (excludedOwnerId != null
                    && owner.getId().equals(excludedOwnerId)) {
                continue;
            }

            total = total.add(
                    share(
                            owner.getOwnershipNumerator(),
                            owner.getOwnershipDenominator()
                    )
            );
        }

        if (total.compareTo(BigDecimal.ONE) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Total ownership shares cannot exceed 100 percent."
            );
        }
    }

    private BigDecimal share(int numerator, int denominator) {
        return BigDecimal.valueOf(numerator)
                .divide(
                        BigDecimal.valueOf(denominator),
                        SHARE_SCALE,
                        RoundingMode.HALF_UP
                );
    }

    private void clearOtherPrimaryContacts(
            UUID propertyId,
            UUID exceptOwnerId
    ) {
        for (OwnerEntity owner :
                repository.findByPropertyIdAndPrimaryContactTrue(propertyId)) {

            if (exceptOwnerId != null
                    && owner.getId().equals(exceptOwnerId)) {
                continue;
            }

            owner.setPrimaryContact(false);
        }

        // Flush before inserting/updating the next primary contact so the
        // database partial unique index cannot be violated by flush ordering.
        repository.flush();
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
