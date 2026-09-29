package com.sakhtyar.knowledge.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "knowledge_relation")
public class KnowledgeRelationEntity {

    @Id
    private UUID id;

    @Column(name = "from_term_id", nullable = false)
    private UUID fromTermId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 60)
    private KnowledgeRelationType relationType;

    @Column(name = "to_term_id", nullable = false)
    private UUID toTermId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private KnowledgeReviewStatus status;

    @Column(name = "source_id")
    private UUID sourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_by", nullable = false, length = 150)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected KnowledgeRelationEntity() {}

    public KnowledgeRelationEntity(
            UUID id, UUID fromTermId, KnowledgeRelationType relationType, UUID toTermId,
            KnowledgeReviewStatus status, UUID sourceId, Map<String, Object> metadata,
            String createdBy, Instant createdAt
    ) {
        this.id = id;
        this.fromTermId = fromTermId;
        this.relationType = relationType;
        this.toTermId = toTermId;
        this.status = status;
        this.sourceId = sourceId;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getFromTermId() { return fromTermId; }
    public KnowledgeRelationType getRelationType() { return relationType; }
    public UUID getToTermId() { return toTermId; }
    public KnowledgeReviewStatus getStatus() { return status; }
    public UUID getSourceId() { return sourceId; }
    public Map<String, Object> getMetadata() { return metadata == null ? Map.of() : Map.copyOf(metadata); }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}