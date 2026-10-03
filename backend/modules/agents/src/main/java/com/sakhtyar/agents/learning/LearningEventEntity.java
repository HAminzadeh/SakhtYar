package com.sakhtyar.agents.learning;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "learning_event")
public class LearningEventEntity {

    @Id
    private UUID id;

    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "input_gateway_request_id")
    private UUID inputGatewayRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false, length = 50)
    private LearningFeedbackType feedbackType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private LearningEventStatus status;

    @Column(name = "field_key", length = 160)
    private String fieldKey;

    @Column(name = "original_value", columnDefinition = "text")
    private String originalValue;

    @Column(name = "corrected_value", columnDefinition = "text")
    private String correctedValue;

    private Integer rating;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "knowledge_candidate_id")
    private UUID knowledgeCandidateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String,Object> payload;

    @Column(name = "created_by", nullable = false, length = 150)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LearningEventEntity() {}

    public LearningEventEntity(
            UUID id,
            UUID caseId,
            UUID conversationId,
            UUID inputGatewayRequestId,
            LearningFeedbackType feedbackType,
            LearningEventStatus status,
            String fieldKey,
            String originalValue,
            String correctedValue,
            Integer rating,
            String note,
            Map<String,Object> payload,
            String createdBy,
            Instant createdAt
    ) {
        this.id=id;
        this.caseId=caseId;
        this.conversationId=conversationId;
        this.inputGatewayRequestId=inputGatewayRequestId;
        this.feedbackType=feedbackType;
        this.status=status;
        this.fieldKey=fieldKey;
        this.originalValue=originalValue;
        this.correctedValue=correctedValue;
        this.rating=rating;
        this.note=note;
        this.payload=payload==null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        this.createdBy=createdBy;
        this.createdAt=createdAt;
    }

    public void attachCandidate(UUID candidateId) {
        this.knowledgeCandidateId=candidateId;
        this.status=LearningEventStatus.CANDIDATE_CREATED;
    }

    public UUID getId(){return id;}
    public UUID getCaseId(){return caseId;}
    public UUID getConversationId(){return conversationId;}
    public UUID getInputGatewayRequestId(){return inputGatewayRequestId;}
    public LearningFeedbackType getFeedbackType(){return feedbackType;}
    public LearningEventStatus getStatus(){return status;}
    public String getFieldKey(){return fieldKey;}
    public String getOriginalValue(){return originalValue;}
    public String getCorrectedValue(){return correctedValue;}
    public Integer getRating(){return rating;}
    public String getNote(){return note;}
    public UUID getKnowledgeCandidateId(){return knowledgeCandidateId;}
    public Map<String,Object> getPayload(){
        return payload==null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
    public String getCreatedBy(){return createdBy;}
    public Instant getCreatedAt(){return createdAt;}
}