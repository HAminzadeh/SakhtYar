package com.sakhtyar.assembly.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cost_assembly_component")
public class CostAssemblyComponentEntity {

    @Id
    private UUID id;

    @Column(name = "assembly_id", nullable = false)
    private UUID assemblyId;

    @Column(name = "material_item_id", nullable = false)
    private UUID materialItemId;

    @Column(name = "variant_id")
    private UUID variantId;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal quantity;

    @Column(name = "waste_factor", nullable = false, precision = 10, scale = 6)
    private BigDecimal wasteFactor;

    @Column(name = "unit_code", nullable = false, length = 40)
    private String unitCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_basis", nullable = false, length = 30)
    private PriceBasis priceBasis;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CostAssemblyComponentEntity() {}

    public CostAssemblyComponentEntity(
            UUID id, UUID assemblyId, UUID materialItemId, UUID variantId,
            BigDecimal quantity, BigDecimal wasteFactor, String unitCode,
            PriceBasis priceBasis, int sortOrder, String note, Instant createdAt
    ) {
        this.id = id;
        this.assemblyId = assemblyId;
        this.materialItemId = materialItemId;
        this.variantId = variantId;
        this.quantity = quantity;
        this.wasteFactor = wasteFactor;
        this.unitCode = unitCode;
        this.priceBasis = priceBasis;
        this.sortOrder = sortOrder;
        this.note = note;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getAssemblyId() { return assemblyId; }
    public UUID getMaterialItemId() { return materialItemId; }
    public UUID getVariantId() { return variantId; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getWasteFactor() { return wasteFactor; }
    public String getUnitCode() { return unitCode; }
    public PriceBasis getPriceBasis() { return priceBasis; }
    public int getSortOrder() { return sortOrder; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}