package com.sakhtyar.agents.learning;

import static com.sakhtyar.agents.api.LearningDtos.*;

import com.sakhtyar.agents.input.PersianInputRequestEntity;
import com.sakhtyar.agents.input.PersianInputRequestRepository;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.knowledge.api.KnowledgeDtos.CreateCandidateRequest;
import com.sakhtyar.knowledge.application.KnowledgeService;
import com.sakhtyar.knowledge.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningLoopService {

    private final LearningEventRepository repository;
    private final PersianInputRequestRepository inputRepository;
    private final KnowledgeService knowledgeService;
    private final AuditService auditService;

    public LearningLoopService(
            LearningEventRepository repository,
            PersianInputRequestRepository inputRepository,
            KnowledgeService knowledgeService,
            AuditService auditService
    ) {
        this.repository=repository;
        this.inputRepository=inputRepository;
        this.knowledgeService=knowledgeService;
        this.auditService=auditService;
    }

    @Transactional
    public LearningEventResponse submit(
            FeedbackRequest request,
            Authentication authentication
    ) {
        LearningCandidatePolicy.validate(request);

        UUID caseId=request.caseId();
        UUID conversationId=request.conversationId();

        if (request.inputGatewayRequestId()!=null) {
            PersianInputRequestEntity input=inputRepository
                    .findById(request.inputGatewayRequestId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Persian input request not found."
                    ));

            if (caseId!=null
                    && input.getCaseId()!=null
                    && !caseId.equals(input.getCaseId())) {
                throw new IllegalArgumentException(
                        "Learning feedback case does not match the Persian input request."
                );
            }

            if (conversationId!=null
                    && input.getConversationId()!=null
                    && !conversationId.equals(input.getConversationId())) {
                throw new IllegalArgumentException(
                        "Learning feedback conversation does not match the Persian input request."
                );
            }

            if (caseId==null) caseId=input.getCaseId();
            if (conversationId==null) conversationId=input.getConversationId();
        }

        LinkedHashMap<String,Object> payload=new LinkedHashMap<>();
        if (request.metadata()!=null) {
            payload.putAll(request.metadata());
        }
        payload.put("proposeKnowledgeCandidate",request.proposeKnowledgeCandidate());

        Instant now=Instant.now();
        LearningEventEntity event=new LearningEventEntity(
                UUID.randomUUID(),
                caseId,
                conversationId,
                request.inputGatewayRequestId(),
                request.feedbackType(),
                LearningEventStatus.RECORDED,
                trim(request.fieldKey()),
                trim(request.originalValue()),
                trim(request.correctedValue()),
                request.rating(),
                trim(request.note()),
                payload,
                actor(authentication),
                now
        );
        repository.save(event);

        if (request.proposeKnowledgeCandidate()) {
            String candidateName=
                    LearningCandidatePolicy.candidateNameFa(request);

            LinkedHashMap<String,Object> normalizedPayload=
                    new LinkedHashMap<>();
            normalizedPayload.put("learningEventId",event.getId().toString());
            normalizedPayload.put("feedbackType",request.feedbackType().name());

            if (request.fieldKey()!=null) {
                normalizedPayload.put("fieldKey",request.fieldKey());
            }
            if (request.originalValue()!=null) {
                normalizedPayload.put("originalValue",request.originalValue());
            }
            if (request.correctedValue()!=null) {
                normalizedPayload.put("correctedValue",request.correctedValue());
            }

            var candidate=knowledgeService.createCandidate(
                    new CreateCandidateRequest(
                            LearningCandidatePolicy.candidateType(request),
                            KnowledgeCandidateOrigin.USER_INPUT,
                            candidateRawInput(request),
                            normalizedPayload,
                            trim(request.proposedTermCode()),
                            candidateName,
                            trim(request.proposedNameEn()),
                            null,
                            null,
                            request.confidence(),
                            candidateReason(request)
                    ),
                    authentication
            );

            event.attachCandidate(candidate.id());
            repository.save(event);
        }

        LinkedHashMap<String,Object> audit=new LinkedHashMap<>();
        audit.put("feedbackType",request.feedbackType().name());
        audit.put("status",event.getStatus().name());
        audit.put(
                "knowledgeCandidateCreated",
                event.getKnowledgeCandidateId()!=null
        );
        if (caseId!=null) audit.put("caseId",caseId.toString());
        if (event.getKnowledgeCandidateId()!=null) {
            audit.put(
                    "knowledgeCandidateId",
                    event.getKnowledgeCandidateId().toString()
            );
        }

        auditService.record(
                "LEARNING_EVENT",
                event.getId(),
                "LEARNING_FEEDBACK_RECORDED",
                audit
        );

        return LearningEventResponse.from(event);
    }

    @Transactional(readOnly=true)
    public LearningEventResponse get(UUID id) {
        return LearningEventResponse.from(
                repository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Learning event not found."
                        ))
        );
    }

    @Transactional(readOnly=true)
    public List<LearningEventResponse> byCase(UUID caseId) {
        return repository.findByCaseIdOrderByCreatedAtDesc(caseId)
                .stream()
                .map(LearningEventResponse::from)
                .toList();
    }

    @Transactional(readOnly=true)
    public List<LearningEventResponse> byInputRequest(UUID inputRequestId) {
        return repository
                .findByInputGatewayRequestIdOrderByCreatedAtDesc(inputRequestId)
                .stream()
                .map(LearningEventResponse::from)
                .toList();
    }

    private String candidateRawInput(FeedbackRequest request) {
        StringBuilder value=new StringBuilder();
        value.append(request.feedbackType().name());

        if (request.originalValue()!=null && !request.originalValue().isBlank()) {
            value.append(" | original=").append(request.originalValue().trim());
        }
        if (request.correctedValue()!=null && !request.correctedValue().isBlank()) {
            value.append(" | corrected=").append(request.correctedValue().trim());
        }
        if (request.note()!=null && !request.note().isBlank()) {
            value.append(" | note=").append(request.note().trim());
        }

        return value.toString();
    }

    private String candidateReason(FeedbackRequest request) {
        if (request.note()!=null && !request.note().isBlank()) {
            return "Learning Loop user feedback: " + request.note().trim();
        }
        return "Learning Loop user feedback: " + request.feedbackType().name();
    }

    private static String actor(Authentication authentication) {
        return authentication==null || authentication.getName()==null
                ? "system"
                : authentication.getName();
    }

    private static String trim(String value) {
        return value==null ? null : value.trim();
    }
}