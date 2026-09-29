package com.sakhtyar.knowledge.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "knowledge_term")
public class KnowledgeTermEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 120)
    private String code;

    @Column(nullable = false, length = 80)
    private String domain;

    @Column(length = 120)
    private String category;

    @Column(name = "name_fa", nullable = false, length = 300)
    private String nameFa;

    @Column(name = "name_en", length = 300)
    private String nameEn;

    @Column(columnDefinition = "text")
    private String definition;

    @Column(name = "unit_code", length = 40)
    private String unitCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private KnowledgeReviewStatus status;

    @Column(precision = 6, scale = 5)
    private BigDecimal confidence;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "provenance_url", length = 3000)
    private String provenanceUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_by", nullable = false, length = 150)
    private String createdBy;

    @Column(name = "updated_by", nullable = false, length = 150)
    private String updatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KnowledgeTermEntity() {}

    public KnowledgeTermEntity(
            UUID id, String code, String domain, String category, String nameFa, String nameEn,
            String definition, String unitCode, KnowledgeReviewStatus status, BigDecimal confidence,
            UUID sourceId, String provenanceUrl, Map<String, Object> metadata,
            String actor, Instant now
    ) {
        this.id = id;
        this.code = code;
        this.domain = domain;
        this.category = category;
        this.nameFa = nameFa;
        this.nameEn = nameEn;
        this.definition = definition;
        this.unitCode = unitCode;
        this.status = status;
        this.confidence = confidence;
        this.sourceId = sourceId;
        this.provenanceUrl = provenanceUrl;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.createdBy = actor;
        this.updatedBy = actor;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            String domain, String category, String nameFa, String nameEn, String definition,
            String unitCode, KnowledgeReviewStatus status, BigDecimal confidence,
            UUID sourceId, String provenanceUrl, Map<String, Object> metadata,
            String actor, Instant now
    ) {
        this.domain = domain;
        this.category = category;
        this.nameFa = nameFa;
        this.nameEn = nameEn;
        this.definition = definition;
        this.unitCode = unitCode;
        this.status = status;
        this.confidence = confidence;
        this.sourceId = sourceId;
        this.provenanceUrl = provenanceUrl;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getDomain() { return domain; }
    public String getCategory() { return category; }
    public String getNameFa() { return nameFa; }
    public String getNameEn() { return nameEn; }
    public String getDefinition() { return definition; }
    public String getUnitCode() { return unitCode; }
    public KnowledgeReviewStatus getStatus() { return status; }
    public BigDecimal getConfidence() { return confidence; }
    public UUID getSourceId() { return sourceId; }
    public String getProvenanceUrl() { return provenanceUrl; }
    public Map<String, Object> getMetadata() { return metadata == null ? Map.of() : Map.copyOf(metadata); }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}