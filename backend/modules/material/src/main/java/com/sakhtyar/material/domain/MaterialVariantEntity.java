package com.sakhtyar.material.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "material_variant")
public class MaterialVariantEntity {

    @Id
    private UUID id;

    @Column(name = "material_item_id", nullable = false)
    private UUID materialItemId;

    @Column(length = 250)
    private String brand;

    @Column(length = 250)
    private String model;

    @Column(length = 160)
    private String grade;

    @Column(length = 250)
    private String manufacturer;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> attributes;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_by", nullable = false, length = 150)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MaterialVariantEntity() {}

    public MaterialVariantEntity(
            UUID id, UUID materialItemId, String brand, String model, String grade,
            String manufacturer, String countryCode, Map<String, Object> attributes,
            boolean active, String createdBy, Instant now
    ) {
        this.id = id;
        this.materialItemId = materialItemId;
        this.brand = brand;
        this.model = model;
        this.grade = grade;
        this.manufacturer = manufacturer;
        this.countryCode = countryCode;
        this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        this.active = active;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getMaterialItemId() { return materialItemId; }
    public String getBrand() { return brand; }
    public String getModel() { return model; }
    public String getGrade() { return grade; }
    public String getManufacturer() { return manufacturer; }
    public String getCountryCode() { return countryCode; }
    public Map<String, Object> getAttributes() { return attributes == null ? Map.of() : Map.copyOf(attributes); }
    public boolean isActive() { return active; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}