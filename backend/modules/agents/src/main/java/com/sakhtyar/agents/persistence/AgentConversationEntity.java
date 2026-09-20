package com.sakhtyar.agents.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_conversation")
public class AgentConversationEntity {

    @Id
    private UUID id;

    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "created_by", nullable = false, length = 150)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentConversationEntity() {
    }

    public AgentConversationEntity(
            UUID id,
            UUID caseId,
            String createdBy,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.caseId = caseId;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch(UUID caseId) {
        if (this.caseId == null && caseId != null) {
            this.caseId = caseId;
        }
        this.updatedAt = Instant.now();
    }
}
