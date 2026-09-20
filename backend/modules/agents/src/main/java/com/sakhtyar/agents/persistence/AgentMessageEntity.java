package com.sakhtyar.agents.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_message")
public class AgentMessageEntity {

    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(nullable = false, length = 30)
    private String role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "payload_text", columnDefinition = "text")
    private String payloadText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentMessageEntity() {
    }

    public AgentMessageEntity(
            UUID id,
            UUID conversationId,
            String role,
            String content,
            String payloadText,
            Instant createdAt
    ) {
        this.id = id;
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.payloadText = payloadText;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getPayloadText() {
        return payloadText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
