package com.sakhtyar.knowledge.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_term_alias")
public class KnowledgeTermAliasEntity {

    @Id
    private UUID id;

    @Column(name = "term_id", nullable = false)
    private UUID termId;

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(nullable = false, length = 300)
    private String alias;

    @Column(name = "alias_normalized", nullable = false, length = 300)
    private String aliasNormalized;

    @Enumerated(EnumType.STRING)
    @Column(name = "alias_type", nullable = false, length = 40)
    private KnowledgeAliasType aliasType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private KnowledgeReviewStatus status;

    @Column(precision = 6, scale = 5)
    private BigDecimal confidence;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "created_by", nullable = false, length = 150)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected KnowledgeTermAliasEntity() {}

    public KnowledgeTermAliasEntity(
            UUID id, UUID termId, String locale, String alias, String aliasNormalized,
            KnowledgeAliasType aliasType, KnowledgeReviewStatus status,
            BigDecimal confidence, UUID sourceId, String createdBy, Instant createdAt
    ) {
        this.id = id;
        this.termId = termId;
        this.locale = locale;
        this.alias = alias;
        this.aliasNormalized = aliasNormalized;
        this.aliasType = aliasType;
        this.status = status;
        this.confidence = confidence;
        this.sourceId = sourceId;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTermId() { return termId; }
    public String getLocale() { return locale; }
    public String getAlias() { return alias; }
    public String getAliasNormalized() { return aliasNormalized; }
    public KnowledgeAliasType getAliasType() { return aliasType; }
    public KnowledgeReviewStatus getStatus() { return status; }
    public BigDecimal getConfidence() { return confidence; }
    public UUID getSourceId() { return sourceId; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}