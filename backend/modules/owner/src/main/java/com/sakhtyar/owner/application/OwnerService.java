package com.sakhtyar.owner.application;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.application.CaseAccessService;
import com.sakhtyar.casefile.application.CaseLifecycle;
import com.sakhtyar.casefile.domain.CaseEntity;
import com.sakhtyar.casefile.domain.CaseRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import com.sakhtyar.owner.api.OwnerDtos.OwnerResponse;
import com.sakhtyar.owner.api.OwnerDtos.UpsertOwnerRequest;
import com.sakhtyar.owner.domain.OwnerEntity;
import com.sakhtyar.owner.domain.OwnerRepository;
import com.sakhtyar.property.domain.PropertyEntity;
import com.sakhtyar.property.domain.PropertyRepository;
import java.util.ArrayList;
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

    private final OwnerRepository repository;
    private final PropertyRepository propertyRepository;
    private final AuditService auditService;
    private final CaseRepository caseRepository;
    private final CaseAccessService caseAccess;

    public OwnerService(
            OwnerRepository repository,
            PropertyRepository propertyRepository,
            AuditService auditService,
            CaseRepository caseRepository,
            CaseAccessService caseAccess
    ) {
        this.repository = repository;
        this.propertyRepository = propertyRepository;
        this.auditService = auditService;
        this.caseRepository = caseRepository;
        this.caseAccess = caseAccess;
    }

    @Transactional(readOnly = true)
    public List<OwnerResponse> list(UUID caseId) {
        PropertyEntity property = requireProperty(caseId, false);
        boolean exposePrivate = canViewPrivateFields(caseId);
        return repository.findByPropertyIdOrderByCreatedAtAsc(property.getId())
                .stream()
                .map(owner -> OwnerResponse.from(owner, exposePrivate))
                .toList();
    }

    @Transactional
    public OwnerResponse create(UUID caseId, UpsertOwnerRequest request) {
        PropertyEntity property = requireProperty(caseId, true);

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

        repository.saveAndFlush(entity);

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
        PropertyEntity property = requireProperty(caseId, true);
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
        repository.flush(); // Surface DB conflicts before committed success audit.

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
        PropertyEntity property = requireProperty(caseId, true);
        OwnerEntity entity = requireOwner(property.getId(), ownerId);

        repository.delete(entity);
        repository.flush();

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

    /** All paths, including direct service calls, enforce the same case ACL. */
    private PropertyEntity requireProperty(UUID caseId, boolean write) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_TESTER".equals(a.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "TESTER must use an isolated TEST owner executor");
        }
        if (auth == null || !auth.isAuthenticated() || auth.getAuthorities() == null
                || auth.getAuthorities().stream().noneMatch(a ->
                        (write ? "OWNER_WRITE" : "OWNER_READ").equals(a.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found.");
        }
        // Same parent lock and lock order as Casefile/Property. Protect totals,
        // primary contact uniqueness and first-owner creation from races.
        CaseEntity c = (write ? caseRepository.findByIdForUpdate(caseId)
                : caseRepository.findById(caseId)).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found."));
        if (!"REAL".equals(c.getDataScope())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found.");
        }
        if (write) {
            caseAccess.requireWrite(c);
            CaseLifecycle.requireEditable(c.getStatus(), c.getStatus());
        } else {
            caseAccess.requireRead(c);
        }
        PropertyEntity property = propertyRepository.findByCaseId(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Property not found for case. Save property information first."));
        if (!"REAL".equals(property.getDataScope()) || !caseId.equals(property.getCaseId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found.");
        }
        return property;
    }

    /** Read membership must not automatically disclose owners' national IDs. */
    private boolean canViewPrivateFields(UUID caseId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null || auth.getAuthorities().stream()
                .noneMatch(a -> "OWNER_WRITE".equals(a.getAuthority()))) return false;
        return caseRepository.findById(caseId).map(c -> {
            try {
                caseAccess.requireWrite(c);
                return true;
            } catch (ResponseStatusException denied) {
                return false;
            }
        }).orElse(false);
    }

    private OwnerEntity requireOwner(UUID propertyId, UUID ownerId) {
        return repository.findByIdAndPropertyId(ownerId, propertyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Owner not found."
                ));
    }

    private void validateShareValues(UpsertOwnerRequest request) {
        if (request.ownershipNumerator() <= 0 || request.ownershipDenominator() <= 0
                || request.ownershipNumerator() > request.ownershipDenominator()) {
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
        List<OwnershipFractions.Share> shares = new ArrayList<>();
        shares.add(new OwnershipFractions.Share(newNumerator, newDenominator));
        for (OwnerEntity owner : repository.findByPropertyIdOrderByCreatedAtAsc(propertyId)) {
            if (excludedOwnerId != null && owner.getId().equals(excludedOwnerId)) continue;
            shares.add(new OwnershipFractions.Share(
                    owner.getOwnershipNumerator(), owner.getOwnershipDenominator()));
        }
        if (OwnershipFractions.exceedsWhole(shares)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Total ownership shares cannot exceed 100 percent.");
        }
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
