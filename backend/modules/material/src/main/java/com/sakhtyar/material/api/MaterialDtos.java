package com.sakhtyar.material.api;

import com.sakhtyar.knowledge.domain.KnowledgeReviewStatus;
import com.sakhtyar.material.domain.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class MaterialDtos {

    private MaterialDtos() {}

    public record UpsertMaterialRequest(
            @NotBlank @Size(max = 120) String code,
            @NotBlank @Size(max = 400) String nameFa,
            @Size(max = 400) String nameEn,
            @NotBlank @Size(max = 160) String category,
            @NotBlank @Size(max = 40) String unitCode,
            UUID knowledgeTermId,
            boolean active,
            Map<String, Object> metadata
    ) {}

    public record CreateVariantRequest(
            @Size(max = 250) String brand,
            @Size(max = 250) String model,
            @Size(max = 160) String grade,
            @Size(max = 250) String manufacturer,
            @Size(min = 2, max = 2) String countryCode,
            Map<String, Object> attributes,
            boolean active
    ) {}

    public record CreatePriceObservationRequest(
            UUID variantId,
            @NotNull MaterialPriceType priceType,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotBlank @Size(min=3,max=8) String currencyCode,
            @NotBlank @Size(max = 40) String unitCode,
            @NotNull @DecimalMin(value = "0.000001") BigDecimal quantityBasis,
            @Size(max = 160) String province,
            @Size(max = 160) String city,
            @NotNull UUID sourceId,
            @Size(max = 3000) String sourceUrl,
            @NotNull Instant observedAt,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
            @NotNull PriceObservationOrigin origin,
            Map<String, Object> rawPayload
    ) {}

    public record ReviewPriceObservationRequest(
            @NotNull KnowledgeReviewStatus status,
            @Size(max = 4000) String note
    ) {}

    public record ReviewAnomalyRequest(
            @NotNull PriceAnomalyStatus status,
            @Size(max = 4000) String note
    ) {}

    public record MaterialResponse(
            UUID id, String code, String nameFa, String nameEn, String category,
            String unitCode, UUID knowledgeTermId, boolean active,
            Map<String, Object> metadata, String createdBy,
            Instant createdAt, Instant updatedAt
    ) {
        public static MaterialResponse from(MaterialItemEntity e) {
            return new MaterialResponse(
                    e.getId(), e.getCode(), e.getNameFa(), e.getNameEn(), e.getCategory(),
                    e.getUnitCode(), e.getKnowledgeTermId(), e.isActive(), e.getMetadata(),
                    e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedAt()
            );
        }
    }

    public record VariantResponse(
            UUID id, UUID materialItemId, String brand, String model, String grade,
            String manufacturer, String countryCode, Map<String, Object> attributes,
            boolean active, String createdBy, Instant createdAt, Instant updatedAt
    ) {
        public static VariantResponse from(MaterialVariantEntity e) {
            return new VariantResponse(
                    e.getId(), e.getMaterialItemId(), e.getBrand(), e.getModel(), e.getGrade(),
                    e.getManufacturer(), e.getCountryCode(), e.getAttributes(), e.isActive(),
                    e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedAt()
            );
        }
    }

    public record PriceObservationResponse(
            UUID id, UUID materialItemId, UUID variantId, MaterialPriceType priceType,
            BigDecimal amount, String currencyCode, String unitCode, BigDecimal quantityBasis,
            String province, String city, UUID sourceId, String sourceUrl,
            Instant observedAt, Instant collectedAt, BigDecimal confidence,
            KnowledgeReviewStatus reviewStatus, PriceObservationOrigin origin,
            Map<String, Object> rawPayload, String createdBy,
            String reviewedBy, Instant reviewedAt, String reviewNote
    ) {
        public static PriceObservationResponse from(MaterialPriceObservationEntity e) {
            return new PriceObservationResponse(
                    e.getId(), e.getMaterialItemId(), e.getVariantId(), e.getPriceType(),
                    e.getAmount(), e.getCurrencyCode(), e.getUnitCode(), e.getQuantityBasis(),
                    e.getProvince(), e.getCity(), e.getSourceId(), e.getSourceUrl(),
                    e.getObservedAt(), e.getCollectedAt(), e.getConfidence(),
                    e.getReviewStatus(), e.getOrigin(), e.getRawPayload(), e.getCreatedBy(),
                    e.getReviewedBy(), e.getReviewedAt(), e.getReviewNote()
            );
        }
    }

    public record PriceAggregateResponse(
            UUID id, UUID materialItemId, UUID variantId, MaterialPriceType priceType,
            String currencyCode, String unitCode, String province, String city,
            int sampleCount, BigDecimal minAmount, BigDecimal maxAmount,
            BigDecimal averageAmount, BigDecimal medianAmount,
            Instant periodStart, Instant periodEnd, String algorithmVersion, Instant calculatedAt
    ) {
        public static PriceAggregateResponse from(MaterialPriceAggregateEntity e) {
            return new PriceAggregateResponse(
                    e.getId(), e.getMaterialItemId(), e.getVariantId(), e.getPriceType(),
                    e.getCurrencyCode(), e.getUnitCode(), e.getProvince(), e.getCity(),
                    e.getSampleCount(), e.getMinAmount(), e.getMaxAmount(),
                    e.getAverageAmount(), e.getMedianAmount(), e.getPeriodStart(),
                    e.getPeriodEnd(), e.getAlgorithmVersion(), e.getCalculatedAt()
            );
        }
    }

    public record PriceAnomalyResponse(
            UUID id, UUID observationId, String anomalyType, PriceAnomalySeverity severity,
            BigDecimal score, PriceAnomalyStatus status, String reason,
            String reviewedBy, Instant reviewedAt, String reviewNote, Instant createdAt
    ) {
        public static PriceAnomalyResponse from(MaterialPriceAnomalyEntity e) {
            return new PriceAnomalyResponse(
                    e.getId(), e.getObservationId(), e.getAnomalyType(), e.getSeverity(),
                    e.getScore(), e.getStatus(), e.getReason(), e.getReviewedBy(),
                    e.getReviewedAt(), e.getReviewNote(), e.getCreatedAt()
            );
        }
    }
}