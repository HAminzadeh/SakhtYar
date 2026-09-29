package com.sakhtyar.knowledge.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "knowledge_term_revision")
public class KnowledgeTermRevisionEntity {

    @Id
    private UUID id;

    @Column(name = "term_id", nullable = false)
    private UUID termId;

    @Column(name = "revision_no", nullable = false)
    private int revisionNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> snapshot;

    @Column(name = "change_reason", length = 500)
    private String changeReason;

    @Column(name = "changed_by", nullable = false, length = 150)
    private String changedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected KnowledgeTermRevisionEntity() {}

    public KnowledgeTermRevisionEntity(
            UUID id, UUID termId, int revisionNo, Map<String, Object> snapshot,
            String changeReason, String changedBy, Instant createdAt
    ) {
        this.id = id;
        this.termId = termId;
        this.revisionNo = revisionNo;
        this.snapshot = Map.copyOf(snapshot);
        this.changeReason = changeReason;
        this.changedBy = changedBy;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTermId() { return termId; }
    public int getRevisionNo() { return revisionNo; }
    public Map<String, Object> getSnapshot() { return Map.copyOf(snapshot); }
    public String getChangeReason() { return changeReason; }
    public String getChangedBy() { return changedBy; }
    public Instant getCreatedAt() { return createdAt; }
}