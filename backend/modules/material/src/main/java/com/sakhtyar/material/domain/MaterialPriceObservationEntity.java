package com.sakhtyar.material.domain;

import com.sakhtyar.knowledge.domain.KnowledgeReviewStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "material_price_observation")
public class MaterialPriceObservationEntity {

    @Id
    private UUID id;

    @Column(name = "material_item_id", nullable = false)
    private UUID materialItemId;

    @Column(name = "variant_id")
    private UUID variantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_type", nullable = false, length = 40)
    private MaterialPriceType priceType;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 8)
    private String currencyCode;
    @Column(name="currency_id", insertable=false, updatable=false)
    private UUID currencyId;

    @Column(name = "unit_code", nullable = false, length = 40)
    private String unitCode;

    @Column(name = "quantity_basis", nullable = false, precision = 20, scale = 6)
    private BigDecimal quantityBasis;

    @Column(length = 160)
    private String province;

    @Column(length = 160)
    private String city;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "source_url", length = 3000)
    private String sourceUrl;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 40)
    private KnowledgeReviewStatus reviewStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PriceObservationOrigin origin;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> rawPayload;

    @Column(name = "created_by", nullable = false, length = 150)
    private String createdBy;

    @Column(name = "reviewed_by", length = 150)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;

    protected MaterialPriceObservationEntity() {}

    public MaterialPriceObservationEntity(
            UUID id, UUID materialItemId, UUID variantId, MaterialPriceType priceType,
            BigDecimal amount, String currencyCode, String unitCode, BigDecimal quantityBasis,
            String province, String city, UUID sourceId, String sourceUrl, Instant observedAt,
            Instant collectedAt, BigDecimal confidence, PriceObservationOrigin origin,
            Map<String, Object> rawPayload, String createdBy
    ) {
        this.id = id;
        this.materialItemId = materialItemId;
        this.variantId = variantId;
        this.priceType = priceType;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.unitCode = unitCode;
        this.quantityBasis = quantityBasis;
        this.province = province;
        this.city = city;
        this.sourceId = sourceId;
        this.sourceUrl = sourceUrl;
        this.observedAt = observedAt;
        this.collectedAt = collectedAt;
        this.confidence = confidence;
        this.reviewStatus = KnowledgeReviewStatus.PENDING_REVIEW;
        this.origin = origin;
        this.rawPayload = rawPayload == null ? Map.of() : Map.copyOf(rawPayload);
        this.createdBy = createdBy;
    }

    public void review(KnowledgeReviewStatus status, String reviewer, String note, Instant now) {
        if (status != KnowledgeReviewStatus.APPROVED && status != KnowledgeReviewStatus.REJECTED) {
            throw new IllegalArgumentException("Price observation review status must be APPROVED or REJECTED.");
        }
        this.reviewStatus = status;
        this.reviewedBy = reviewer;
        this.reviewNote = note;
        this.reviewedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getMaterialItemId() { return materialItemId; }
    public UUID getVariantId() { return variantId; }
    public MaterialPriceType getPriceType() { return priceType; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrencyCode() { return currencyCode; }
    public String getUnitCode() { return unitCode; }
    public BigDecimal getQuantityBasis() { return quantityBasis; }
    public String getProvince() { return province; }
    public String getCity() { return city; }
    public UUID getSourceId() { return sourceId; }
    public String getSourceUrl() { return sourceUrl; }
    public Instant getObservedAt() { return observedAt; }
    public Instant getCollectedAt() { return collectedAt; }
    public BigDecimal getConfidence() { return confidence; }
    public KnowledgeReviewStatus getReviewStatus() { return reviewStatus; }
    public PriceObservationOrigin getOrigin() { return origin; }
    public Map<String, Object> getRawPayload() { return rawPayload == null ? Map.of() : Map.copyOf(rawPayload); }
    public String getCreatedBy() { return createdBy; }
    public String getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewNote() { return reviewNote; }

    public UUID getCurrencyId(){return currencyId;}
}