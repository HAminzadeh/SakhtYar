package com.sakhtyar.assembly.domain;

import com.sakhtyar.material.domain.MaterialPriceType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cost_estimate")
public class CostEstimateEntity {

    @Id
    private UUID id;

    @Column(name = "assembly_id", nullable = false)
    private UUID assemblyId;

    @Column(name = "requested_quantity", nullable = false, precision = 20, scale = 6)
    private BigDecimal requestedQuantity;

    @Column(name = "output_unit_code", nullable = false, length = 40)
    private String outputUnitCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_type", nullable = false, length = 40)
    private MaterialPriceType priceType;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(length = 160)
    private String province;

    @Column(length = 160)
    private String city;

    @Column(name = "unit_cost", nullable = false, precision = 20, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "total_cost", nullable = false, precision = 20, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "algorithm_version", nullable = false, length = 40)
    private String algorithmVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CostEstimateStatus status;

    @Column(name = "calculated_by", nullable = false, length = 150)
    private String calculatedBy;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    protected CostEstimateEntity() {}

    public CostEstimateEntity(
            UUID id, UUID assemblyId, BigDecimal requestedQuantity, String outputUnitCode,
            MaterialPriceType priceType, String currencyCode, String province, String city,
            BigDecimal unitCost, BigDecimal totalCost, String algorithmVersion,
            String calculatedBy, Instant calculatedAt
    ) {
        this.id = id;
        this.assemblyId = assemblyId;
        this.requestedQuantity = requestedQuantity;
        this.outputUnitCode = outputUnitCode;
        this.priceType = priceType;
        this.currencyCode = currencyCode;
        this.province = province;
        this.city = city;
        this.unitCost = unitCost;
        this.totalCost = totalCost;
        this.algorithmVersion = algorithmVersion;
        this.status = CostEstimateStatus.CALCULATED;
        this.calculatedBy = calculatedBy;
        this.calculatedAt = calculatedAt;
    }

    public UUID getId() { return id; }
    public UUID getAssemblyId() { return assemblyId; }
    public BigDecimal getRequestedQuantity() { return requestedQuantity; }
    public String getOutputUnitCode() { return outputUnitCode; }
    public MaterialPriceType getPriceType() { return priceType; }
    public String getCurrencyCode() { return currencyCode; }
    public String getProvince() { return province; }
    public String getCity() { return city; }
    public BigDecimal getUnitCost() { return unitCost; }
    public BigDecimal getTotalCost() { return totalCost; }
    public String getAlgorithmVersion() { return algorithmVersion; }
    public CostEstimateStatus getStatus() { return status; }
    public String getCalculatedBy() { return calculatedBy; }
    public Instant getCalculatedAt() { return calculatedAt; }
}