package com.sakhtyar.audit.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_event")
public class AuditEventEntity {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id")
    private UUID aggregateId;

    @Column(nullable = false, length = 150)
    private String action;

    @Column(nullable = false, length = 150)
    private String actor;

    @Column(name = "payload_text", columnDefinition = "text")
    private String payloadText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditEventEntity() {
    }

    public AuditEventEntity(
            UUID id,
            String aggregateType,
            UUID aggregateId,
            String action,
            String actor,
            String payloadText,
            Instant createdAt
    ) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.action = action;
        this.actor = actor;
        this.payloadText = payloadText;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public UUID getAggregateId() { return aggregateId; }
    public String getAction() { return action; }
    public String getActor() { return actor; }
    public String getPayloadText() { return payloadText; }
    public Instant getCreatedAt() { return createdAt; }
}
