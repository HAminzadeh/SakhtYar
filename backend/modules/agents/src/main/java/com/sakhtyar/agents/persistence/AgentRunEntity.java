package com.sakhtyar.agents.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_run")
public class AgentRunEntity {

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false, unique = true)
    private UUID requestId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "schema_version", nullable = false, length = 30)
    private String schemaVersion;

    @Column(length = 80)
    private String intent;

    @Column(length = 80)
    private String workflow;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "input_text", nullable = false, columnDefinition = "text")
    private String inputText;

    @Column(name = "output_text", nullable = false, columnDefinition = "text")
    private String outputText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentRunEntity() {
    }

    public AgentRunEntity(
            UUID id,
            UUID requestId,
            UUID conversationId,
            UUID caseId,
            String schemaVersion,
            String intent,
            String workflow,
            String status,
            long durationMs,
            String inputText,
            String outputText,
            Instant createdAt
    ) {
        this.id = id;
        this.requestId = requestId;
        this.conversationId = conversationId;
        this.caseId = caseId;
        this.schemaVersion = schemaVersion;
        this.intent = intent;
        this.workflow = workflow;
        this.status = status;
        this.durationMs = durationMs;
        this.inputText = inputText;
        this.outputText = outputText;
        this.createdAt = createdAt;
    }
}
