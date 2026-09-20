package com.sakhtyar.property.api;

import com.sakhtyar.property.domain.PropertyEntity;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
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
            @DecimalMin(value = "0.01", inclusive = true)
            BigDecimal landAreaM2,
            @DecimalMin(value = "0.01", inclusive = true)
            BigDecimal frontageM,
            @DecimalMin(value = "0.01", inclusive = true)
            BigDecimal passageWidthM,
            @DecimalMin(value = "0.01", inclusive = true)
            BigDecimal buildingAreaM2,
            @Min(1000) @Max(2500)
            Integer constructionYear,
            @Min(0) @Max(200)
            Integer existingFloors,
            @Min(0) @Max(10000)
            Integer existingUnits,
            @Pattern(
                    regexp = "NORTH|SOUTH|EAST|WEST|NORTH_EAST|NORTH_WEST|SOUTH_EAST|SOUTH_WEST"
            )
            String orientation,
            @Size(max = 80)
            String propertyType,
            @Size(max = 80)
            String buildingCondition,
            @Size(max = 100) String registryMainNo,
            @Size(max = 100) String registrySubNo,
            @Size(max = 100) String registrySection,
            @Size(max = 20) String postalCode,
            @DecimalMin("-90.0") @DecimalMax("90.0")
            BigDecimal latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0")
            BigDecimal longitude,
            Map<String, Object> attributes
    ) {
    }

    public record MergePropertyFactsRequest(
            Map<String, Object> facts
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
            BigDecimal frontageM,
            BigDecimal passageWidthM,
            BigDecimal buildingAreaM2,
            Integer constructionYear,
            Integer existingFloors,
            Integer existingUnits,
            String orientation,
            String propertyType,
            String buildingCondition,
            String registryMainNo,
            String registrySubNo,
            String registrySection,
            String postalCode,
            BigDecimal latitude,
            BigDecimal longitude,
            String attributesSchemaVersion,
            Map<String, Object> attributes,
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
                    entity.getFrontageM(),
                    entity.getPassageWidthM(),
                    entity.getBuildingAreaM2(),
                    entity.getConstructionYear(),
                    entity.getExistingFloors(),
                    entity.getExistingUnits(),
                    entity.getOrientation(),
                    entity.getPropertyType(),
                    entity.getBuildingCondition(),
                    entity.getRegistryMainNo(),
                    entity.getRegistrySubNo(),
                    entity.getRegistrySection(),
                    entity.getPostalCode(),
                    entity.getLatitude(),
                    entity.getLongitude(),
                    entity.getAttributesSchemaVersion(),
                    entity.getAttributes(),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt()
            );
        }
    }
}
