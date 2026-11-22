package com.sakhtyar.assembly.api;

import com.sakhtyar.assembly.domain.*;
import com.sakhtyar.material.domain.MaterialPriceType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AssemblyDtos {

    private AssemblyDtos() {}

    public record UpsertAssemblyRequest(
            @NotBlank @Size(max = 120) String code,
            @NotBlank @Size(max = 400) String nameFa,
            @Size(max = 400) String nameEn,
            @NotBlank @Size(max = 160) String category,
            @NotBlank @Size(max = 40) String outputUnitCode,
            @Size(max = 10000) String description,
            boolean active,
            Map<String, Object> metadata
    ) {}

    public record CreateComponentRequest(
            @NotNull UUID materialItemId,
            UUID variantId,
            @NotNull @DecimalMin("0.000001") BigDecimal quantity,
            @NotNull @DecimalMin("0.0") @DecimalMax("10.0") BigDecimal wasteFactor,
            @NotBlank @Size(max = 40) String unitCode,
            @NotNull PriceBasis priceBasis,
            @Min(0) int sortOrder,
            @Size(max = 4000) String note
    ) {}

    public record CalculateCostRequest(
            @NotNull @DecimalMin("0.000001") BigDecimal quantity,
            @NotNull MaterialPriceType priceType,
            @NotBlank @Size(min=3,max=8) String currencyCode,
            @Size(max = 160) String province,
            @Size(max = 160) String city
    ) {}

    public record AssemblyResponse(
            UUID id, String code, String nameFa, String nameEn, String category,
            String outputUnitCode, String description, boolean active,
            Map<String, Object> metadata, String createdBy, Instant createdAt, Instant updatedAt
    ) {
        public static AssemblyResponse from(CostAssemblyEntity e) {
            return new AssemblyResponse(
                    e.getId(), e.getCode(), e.getNameFa(), e.getNameEn(), e.getCategory(),
                    e.getOutputUnitCode(), e.getDescription(), e.isActive(), e.getMetadata(),
                    e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedAt()
            );
        }
    }

    public record ComponentResponse(
            UUID id, UUID assemblyId, UUID materialItemId, UUID variantId,
            BigDecimal quantity, BigDecimal wasteFactor, String unitCode,
            PriceBasis priceBasis, int sortOrder, String note, Instant createdAt
    ) {
        public static ComponentResponse from(CostAssemblyComponentEntity e) {
            return new ComponentResponse(
                    e.getId(), e.getAssemblyId(), e.getMaterialItemId(), e.getVariantId(),
                    e.getQuantity(), e.getWasteFactor(), e.getUnitCode(), e.getPriceBasis(),
                    e.getSortOrder(), e.getNote(), e.getCreatedAt()
            );
        }
    }

    public record EstimateLineResponse(
            UUID id, UUID assemblyComponentId, UUID materialItemId, UUID variantId,
            UUID aggregateId, BigDecimal baseQuantity, BigDecimal wasteFactor,
            BigDecimal effectiveQuantity, String unitCode, BigDecimal unitPrice,
            BigDecimal lineTotal, PriceBasis priceBasis, int sourceSampleCount,
            Instant sourcePeriodStart, Instant sourcePeriodEnd
    ) {
        public static EstimateLineResponse from(CostEstimateLineEntity e) {
            return new EstimateLineResponse(
                    e.getId(), e.getAssemblyComponentId(), e.getMaterialItemId(),
                    e.getVariantId(), e.getAggregateId(), e.getBaseQuantity(),
                    e.getWasteFactor(), e.getEffectiveQuantity(), e.getUnitCode(),
                    e.getUnitPrice(), e.getLineTotal(), e.getPriceBasis(),
                    e.getSourceSampleCount(), e.getSourcePeriodStart(), e.getSourcePeriodEnd()
            );
        }
    }

    public record EstimateResponse(
            UUID id, UUID assemblyId, BigDecimal requestedQuantity, String outputUnitCode,
            MaterialPriceType priceType, String currencyCode, String province, String city,
            BigDecimal unitCost, BigDecimal totalCost, String algorithmVersion,
            CostEstimateStatus status, String calculatedBy, Instant calculatedAt,
            List<EstimateLineResponse> lines
    ) {}
}