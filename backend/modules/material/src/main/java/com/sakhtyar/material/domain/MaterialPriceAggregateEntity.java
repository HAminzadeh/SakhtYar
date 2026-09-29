package com.sakhtyar.material.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "material_price_aggregate")
public class MaterialPriceAggregateEntity {

    @Id
    private UUID id;

    @Column(name = "material_item_id", nullable = false)
    private UUID materialItemId;

    @Column(name = "variant_id")
    private UUID variantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_type", nullable = false, length = 40)
    private MaterialPriceType priceType;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "unit_code", nullable = false, length = 40)
    private String unitCode;

    @Column(length = 160)
    private String province;

    @Column(length = 160)
    private String city;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    @Column(name = "min_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "average_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal averageAmount;

    @Column(name = "median_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal medianAmount;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "algorithm_version", nullable = false, length = 40)
    private String algorithmVersion;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    protected MaterialPriceAggregateEntity() {}

    public MaterialPriceAggregateEntity(
            UUID id, UUID materialItemId, UUID variantId, MaterialPriceType priceType,
            String currencyCode, String unitCode, String province, String city,
            int sampleCount, BigDecimal minAmount, BigDecimal maxAmount,
            BigDecimal averageAmount, BigDecimal medianAmount, Instant periodStart,
            Instant periodEnd, String algorithmVersion, Instant calculatedAt
    ) {
        this.id = id;
        this.materialItemId = materialItemId;
        this.variantId = variantId;
        this.priceType = priceType;
        this.currencyCode = currencyCode;
        this.unitCode = unitCode;
        this.province = province;
        this.city = city;
        this.sampleCount = sampleCount;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.averageAmount = averageAmount;
        this.medianAmount = medianAmount;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.algorithmVersion = algorithmVersion;
        this.calculatedAt = calculatedAt;
    }

    public UUID getId() { return id; }
    public UUID getMaterialItemId() { return materialItemId; }
    public UUID getVariantId() { return variantId; }
    public MaterialPriceType getPriceType() { return priceType; }
    public String getCurrencyCode() { return currencyCode; }
    public String getUnitCode() { return unitCode; }
    public String getProvince() { return province; }
    public String getCity() { return city; }
    public int getSampleCount() { return sampleCount; }
    public BigDecimal getMinAmount() { return minAmount; }
    public BigDecimal getMaxAmount() { return maxAmount; }
    public BigDecimal getAverageAmount() { return averageAmount; }
    public BigDecimal getMedianAmount() { return medianAmount; }
    public Instant getPeriodStart() { return periodStart; }
    public Instant getPeriodEnd() { return periodEnd; }
    public String getAlgorithmVersion() { return algorithmVersion; }
    public Instant getCalculatedAt() { return calculatedAt; }
}