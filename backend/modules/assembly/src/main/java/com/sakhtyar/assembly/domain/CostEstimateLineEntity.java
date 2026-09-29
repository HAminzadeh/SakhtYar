package com.sakhtyar.assembly.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cost_estimate_line")
public class CostEstimateLineEntity {

    @Id
    private UUID id;

    @Column(name = "estimate_id", nullable = false)
    private UUID estimateId;

    @Column(name = "assembly_component_id", nullable = false)
    private UUID assemblyComponentId;

    @Column(name = "material_item_id", nullable = false)
    private UUID materialItemId;

    @Column(name = "variant_id")
    private UUID variantId;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "base_quantity", nullable = false, precision = 20, scale = 6)
    private BigDecimal baseQuantity;

    @Column(name = "waste_factor", nullable = false, precision = 10, scale = 6)
    private BigDecimal wasteFactor;

    @Column(name = "effective_quantity", nullable = false, precision = 20, scale = 6)
    private BigDecimal effectiveQuantity;

    @Column(name = "unit_code", nullable = false, length = 40)
    private String unitCode;

    @Column(name = "unit_price", nullable = false, precision = 20, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 20, scale = 2)
    private BigDecimal lineTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_basis", nullable = false, length = 30)
    private PriceBasis priceBasis;

    @Column(name = "source_sample_count", nullable = false)
    private int sourceSampleCount;

    @Column(name = "source_period_start", nullable = false)
    private Instant sourcePeriodStart;

    @Column(name = "source_period_end", nullable = false)
    private Instant sourcePeriodEnd;

    protected CostEstimateLineEntity() {}

    public CostEstimateLineEntity(
            UUID id, UUID estimateId, UUID assemblyComponentId, UUID materialItemId,
            UUID variantId, UUID aggregateId, BigDecimal baseQuantity,
            BigDecimal wasteFactor, BigDecimal effectiveQuantity, String unitCode,
            BigDecimal unitPrice, BigDecimal lineTotal, PriceBasis priceBasis,
            int sourceSampleCount, Instant sourcePeriodStart, Instant sourcePeriodEnd
    ) {
        this.id = id;
        this.estimateId = estimateId;
        this.assemblyComponentId = assemblyComponentId;
        this.materialItemId = materialItemId;
        this.variantId = variantId;
        this.aggregateId = aggregateId;
        this.baseQuantity = baseQuantity;
        this.wasteFactor = wasteFactor;
        this.effectiveQuantity = effectiveQuantity;
        this.unitCode = unitCode;
        this.unitPrice = unitPrice;
        this.lineTotal = lineTotal;
        this.priceBasis = priceBasis;
        this.sourceSampleCount = sourceSampleCount;
        this.sourcePeriodStart = sourcePeriodStart;
        this.sourcePeriodEnd = sourcePeriodEnd;
    }

    public UUID getId() { return id; }
    public UUID getEstimateId() { return estimateId; }
    public UUID getAssemblyComponentId() { return assemblyComponentId; }
    public UUID getMaterialItemId() { return materialItemId; }
    public UUID getVariantId() { return variantId; }
    public UUID getAggregateId() { return aggregateId; }
    public BigDecimal getBaseQuantity() { return baseQuantity; }
    public BigDecimal getWasteFactor() { return wasteFactor; }
    public BigDecimal getEffectiveQuantity() { return effectiveQuantity; }
    public String getUnitCode() { return unitCode; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public PriceBasis getPriceBasis() { return priceBasis; }
    public int getSourceSampleCount() { return sourceSampleCount; }
    public Instant getSourcePeriodStart() { return sourcePeriodStart; }
    public Instant getSourcePeriodEnd() { return sourcePeriodEnd; }
}