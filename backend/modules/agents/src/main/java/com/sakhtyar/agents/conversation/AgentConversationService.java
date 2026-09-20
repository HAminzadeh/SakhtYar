package com.sakhtyar.agents.conversation;

import com.sakhtyar.agents.core.AgentContracts;
import com.sakhtyar.agents.core.AgentRequest;
import com.sakhtyar.agents.orchestrator.AgentWorkflowResult;
import com.sakhtyar.agents.persistence.AgentConversationEntity;
import com.sakhtyar.agents.persistence.AgentConversationRepository;
import com.sakhtyar.agents.persistence.AgentMessageEntity;
import com.sakhtyar.agents.persistence.AgentMessageRepository;
import com.sakhtyar.agents.persistence.AgentRunEntity;
import com.sakhtyar.agents.persistence.AgentRunRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AgentConversationService {

    private final AgentConversationRepository conversationRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentRunRepository runRepository;
    private final ObjectMapper objectMapper;

    public AgentConversationService(
            AgentConversationRepository conversationRepository,
            AgentMessageRepository messageRepository,
            AgentRunRepository runRepository,
            ObjectMapper objectMapper
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.runRepository = runRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UUID ensureConversation(UUID conversationId, UUID caseId) {
        UUID id = conversationId == null ? UUID.randomUUID() : conversationId;
        AgentConversationEntity entity = conversationRepository.findById(id)
                .orElseGet(() -> {
                    Instant now = Instant.now();
                    return new AgentConversationEntity(
                            id,
                            caseId,
                            currentActor(),
                            now,
                            now
                    );
                });
        entity.touch(caseId);
        conversationRepository.save(entity);
        return id;
    }

    @Transactional
    public void addUserMessage(UUID conversationId, String content) {
        messageRepository.save(new AgentMessageEntity(
                UUID.randomUUID(),
                conversationId,
                "USER",
                content == null ? "" : content,
                null,
                Instant.now()
        ));
    }

    @Transactional
    public void addAssistantMessage(
            UUID conversationId,
            AgentWorkflowResult result
    ) {
        messageRepository.save(new AgentMessageEntity(
                UUID.randomUUID(),
                conversationId,
                "ASSISTANT",
                result.message(),
                toJson(result),
                Instant.now()
        ));
    }

    @Transactional
    public void recordRun(
            AgentRequest request,
            AgentWorkflowResult result,
            long durationMs
    ) {
        runRepository.save(new AgentRunEntity(
                UUID.randomUUID(),
                request.requestId(),
                result.conversationId(),
                request.caseId(),
                AgentContracts.SCHEMA_VERSION,
                result.intent() == null ? null : result.intent().name(),
                result.workflow() == null ? null : result.workflow().name(),
                result.status().name(),
                Math.max(0L, durationMs),
                toJson(Map.of(
                        "message", request.message(),
                        "parameters", request.parameters()
                )),
                toJson(result),
                Instant.now()
        ));
    }

    @Transactional(readOnly = true)
    public List<AgentMessageView> messages(UUID conversationId) {
        if (!conversationRepository.existsById(conversationId)) {
            return List.of();
        }
        return messageRepository
                .findAllByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(entity -> new AgentMessageView(
                        entity.getId(),
                        entity.getConversationId(),
                        entity.getRole(),
                        entity.getContent(),
                        entity.getPayloadText(),
                        entity.getCreatedAt()
                ))
                .toList();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            return "{\"serializationError\":true}";
        }
    }

    private String currentActor() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "system";
        }
        return authentication.getName();
    }

    public record AgentMessageView(
            UUID id,
            UUID conversationId,
            String role,
            String content,
            String payloadText,
            Instant createdAt
    ) {
    }
}
