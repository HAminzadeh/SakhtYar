package com.sakhtyar.owner.api;

import com.sakhtyar.owner.domain.OwnerEntity;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class OwnerDtos {

    private OwnerDtos() {
    }

    public record UpsertOwnerRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 150) String lastName,
            @Size(max = 20) String nationalId,
            @Size(max = 30) String mobile,
            @Min(1) int ownershipNumerator,
            @Min(1) int ownershipDenominator,
            boolean primaryContact
    ) {
    }

    public record OwnerResponse(
            UUID id,
            UUID propertyId,
            String firstName,
            String lastName,
            String nationalId,
            String mobile,
            int ownershipNumerator,
            int ownershipDenominator,
            boolean primaryContact,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static OwnerResponse from(OwnerEntity entity) {
            return new OwnerResponse(
                    entity.getId(),
                    entity.getPropertyId(),
                    entity.getFirstName(),
                    entity.getLastName(),
                    entity.getNationalId(),
                    entity.getMobile(),
                    entity.getOwnershipNumerator(),
                    entity.getOwnershipDenominator(),
                    entity.isPrimaryContact(),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt()
            );
        }
    }
}
