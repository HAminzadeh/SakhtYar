package com.sakhtyar.agents.api;

import com.sakhtyar.agents.learning.*;
import com.sakhtyar.knowledge.domain.KnowledgeCandidateType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class LearningDtos {

    private LearningDtos(){}

    public record FeedbackRequest(
            UUID caseId,
            UUID conversationId,
            UUID inputGatewayRequestId,
            @NotNull LearningFeedbackType feedbackType,
            @Size(max=160) String fieldKey,
            String originalValue,
            String correctedValue,
            @Min(1) @Max(5) Integer rating,
            String note,
            boolean proposeKnowledgeCandidate,
            KnowledgeCandidateType candidateType,
            @Size(max=120) String proposedTermCode,
            @Size(max=300) String proposedNameFa,
            @Size(max=300) String proposedNameEn,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
            Map<String,Object> metadata
    ) {}

    public record LearningEventResponse(
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
            UUID knowledgeCandidateId,
            Map<String,Object> payload,
            String createdBy,
            Instant createdAt
    ) {
        public static LearningEventResponse from(LearningEventEntity e) {
            return new LearningEventResponse(
                    e.getId(),
                    e.getCaseId(),
                    e.getConversationId(),
                    e.getInputGatewayRequestId(),
                    e.getFeedbackType(),
                    e.getStatus(),
                    e.getFieldKey(),
                    e.getOriginalValue(),
                    e.getCorrectedValue(),
                    e.getRating(),
                    e.getNote(),
                    e.getKnowledgeCandidateId(),
                    e.getPayload(),
                    e.getCreatedBy(),
                    e.getCreatedAt()
            );
        }
    }
}