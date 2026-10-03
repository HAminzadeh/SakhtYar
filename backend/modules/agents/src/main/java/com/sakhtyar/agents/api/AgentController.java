package com.sakhtyar.agents.api;

import com.sakhtyar.agents.api.AgentDtos.AgentChatRequest;
import com.sakhtyar.agents.conversation.AgentConversationService;
import com.sakhtyar.agents.conversation.AgentConversationService.AgentMessageView;
import com.sakhtyar.agents.core.AgentContracts;
import com.sakhtyar.agents.core.AgentDescriptor;
import com.sakhtyar.agents.core.AgentRegistry;
import com.sakhtyar.agents.core.AgentRequest;
import com.sakhtyar.agents.core.AgentStatus;
import com.sakhtyar.agents.core.AgentType;
import com.sakhtyar.agents.input.InputGatewayStatus;
import com.sakhtyar.agents.input.PersianInputGateway;
import com.sakhtyar.agents.orchestrator.AgentOrchestrator;
import com.sakhtyar.agents.orchestrator.AgentWorkflowResult;
import com.sakhtyar.agents.provider.AiModelRegistry;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    private final AgentOrchestrator orchestrator;
    private final AgentRegistry registry;
    private final AiModelRegistry modelRegistry;
    private final AgentConversationService conversationService;
    private final PersianInputGateway inputGateway;

    public AgentController(
            AgentOrchestrator orchestrator,
            AgentRegistry registry,
            AiModelRegistry modelRegistry,
            AgentConversationService conversationService,
            PersianInputGateway inputGateway
    ) {
        this.orchestrator = orchestrator;
        this.registry = registry;
        this.modelRegistry = modelRegistry;
        this.conversationService = conversationService;
        this.inputGateway = inputGateway;
    }

    @PostMapping("/chat")
    public AgentWorkflowResult chat(
            @Valid @RequestBody AgentChatRequest request
    ) {
        UUID conversationId = conversationService.ensureConversation(
                request.conversationId(),
                request.caseId()
        );

        conversationService.addUserMessage(
                conversationId,
                request.message()
        );

        var gateway = inputGateway.process(
                request.caseId(),
                conversationId,
                request.message(),
                request.parameters() == null
                        ? Map.of()
                        : request.parameters()
        );

        LinkedHashMap<String,Object> effectiveParameters =
                new LinkedHashMap<>(
                        gateway.canonicalParameters()
                );

        effectiveParameters.put(
                "_inputGatewayRequestId",
                gateway.id().toString()
        );

        AgentRequest agentRequest = new AgentRequest(
                UUID.randomUUID(),
                conversationId,
                request.caseId(),
                gateway.normalizedText(),
                effectiveParameters
        );

        Instant startedAt = Instant.now();
        AgentWorkflowResult result;

        if (gateway.status()
                == InputGatewayStatus.NEEDS_CLARIFICATION) {

            List<String> missing =
                    gateway.clarifications()
                            .stream()
                            .map(item -> item.key())
                            .toList();

            String message = String.join(
                    " ",
                    gateway.clarifications()
                            .stream()
                            .map(item -> item.question())
                            .toList()
            );

            result = new AgentWorkflowResult(
                    AgentContracts.SCHEMA_VERSION,
                    agentRequest.requestId(),
                    conversationId,
                    request.caseId(),
                    null,
                    null,
                    AgentStatus.NEEDS_INPUT,
                    message,
                    effectiveParameters,
                    List.of(),
                    Map.of(),
                    missing
            );
        } else {
            result = orchestrator.execute(agentRequest);
        }

        long durationMs = Duration.between(
                startedAt,
                Instant.now()
        ).toMillis();

        conversationService.addAssistantMessage(
                conversationId,
                result
        );

        conversationService.recordRun(
                agentRequest,
                result,
                durationMs
        );

        return result;
    }
    @GetMapping("/conversations/{conversationId}/messages")
    public List<AgentMessageView> messages(
            @PathVariable UUID conversationId
    ) {
        return conversationService.messages(conversationId);
    }

    @GetMapping
    public List<AgentType> agents() {
        return registry.availableTypes();
    }

    @GetMapping("/registry")
    public List<AgentDescriptor> registry() {
        return registry.descriptors();
    }

    @GetMapping("/ai/status")
    public AiModelRegistry.AiRuntimeStatus aiStatus() {
        return modelRegistry.status();
    }
}
