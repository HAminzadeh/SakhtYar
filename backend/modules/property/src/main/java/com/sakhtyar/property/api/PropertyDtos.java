package com.sakhtyar.property.api;

import com.sakhtyar.property.domain.PropertyEntity;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PropertyDtos {

    private PropertyDtos() {
    }

    public record UpsertPropertyRequest(
            @Size(max = 100) String province,
            @Size(max = 100) String city,
            @Size(max = 100) String district,
            @Size(max = 150) String neighborhood,
            String address,
            @DecimalMin(value = "0.01", inclusive = true) BigDecimal landAreaM2,
            @Size(max = 100) String registryMainNo,
            @Size(max = 100) String registrySubNo,
            @Size(max = 100) String registrySection,
            @Size(max = 20) String postalCode,
            @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude
    ) {
    }

    public record PropertyResponse(
            UUID id,
            UUID caseId,
            String province,
            String city,
            String district,
            String neighborhood,
            String address,
            BigDecimal landAreaM2,
            String registryMainNo,
            String registrySubNo,
            String registrySection,
            String postalCode,
            BigDecimal latitude,
            BigDecimal longitude,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static PropertyResponse from(PropertyEntity entity) {
            return new PropertyResponse(
                    entity.getId(),
                    entity.getCaseId(),
                    entity.getProvince(),
                    entity.getCity(),
                    entity.getDistrict(),
                    entity.getNeighborhood(),
                    entity.getAddress(),
                    entity.getLandAreaM2(),
                    entity.getRegistryMainNo(),
                    entity.getRegistrySubNo(),
                    entity.getRegistrySection(),
                    entity.getPostalCode(),
                    entity.getLatitude(),
                    entity.getLongitude(),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt()
            );
        }
    }
}
