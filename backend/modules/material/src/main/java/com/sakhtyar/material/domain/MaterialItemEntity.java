package com.sakhtyar.material.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "material_item")
public class MaterialItemEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 120)
    private String code;

    @Column(name = "name_fa", nullable = false, length = 400)
    private String nameFa;

    @Column(name = "name_en", length = 400)
    private String nameEn;

    @Column(nullable = false, length = 160)
    private String category;

    @Column(name = "unit_code", nullable = false, length = 40)
    private String unitCode;

    @Column(name = "knowledge_term_id")
    private UUID knowledgeTermId;

    @Column(nullable = false)
    private boolean active;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_by", nullable = false, length = 150)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MaterialItemEntity() {}

    public MaterialItemEntity(
            UUID id, String code, String nameFa, String nameEn, String category,
            String unitCode, UUID knowledgeTermId, boolean active,
            Map<String, Object> metadata, String createdBy, Instant now
    ) {
        this.id = id;
        this.code = code;
        this.nameFa = nameFa;
        this.nameEn = nameEn;
        this.category = category;
        this.unitCode = unitCode;
        this.knowledgeTermId = knowledgeTermId;
        this.active = active;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            String nameFa, String nameEn, String category, String unitCode,
            UUID knowledgeTermId, boolean active, Map<String, Object> metadata, Instant now
    ) {
        this.nameFa = nameFa;
        this.nameEn = nameEn;
        this.category = category;
        this.unitCode = unitCode;
        this.knowledgeTermId = knowledgeTermId;
        this.active = active;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getNameFa() { return nameFa; }
    public String getNameEn() { return nameEn; }
    public String getCategory() { return category; }
    public String getUnitCode() { return unitCode; }
    public UUID getKnowledgeTermId() { return knowledgeTermId; }
    public boolean isActive() { return active; }
    public Map<String, Object> getMetadata() { return metadata == null ? Map.of() : Map.copyOf(metadata); }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}