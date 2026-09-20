package com.sakhtyar.agents.api;

import com.sakhtyar.agents.api.AgentDtos.AgentChatRequest;
import com.sakhtyar.agents.core.AgentRegistry;
import com.sakhtyar.agents.core.AgentRequest;
import com.sakhtyar.agents.core.AgentType;
import com.sakhtyar.agents.orchestrator.AgentOrchestrator;
import com.sakhtyar.agents.orchestrator.AgentWorkflowResult;
import com.sakhtyar.agents.provider.AiModelRegistry;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
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

    public AgentController(
            AgentOrchestrator orchestrator,
            AgentRegistry registry,
            AiModelRegistry modelRegistry
    ) {
        this.orchestrator = orchestrator;
        this.registry = registry;
        this.modelRegistry = modelRegistry;
    }

    @PostMapping("/chat")
    public AgentWorkflowResult chat(
            @Valid @RequestBody AgentChatRequest request
    ) {
        AgentRequest agentRequest = new AgentRequest(
                UUID.randomUUID(),
                request.conversationId(),
                request.caseId(),
                request.message(),
                request.parameters() == null ? Map.of() : request.parameters()
        );
        return orchestrator.execute(agentRequest);
    }

    @GetMapping
    public List<AgentType> agents() {
        return registry.availableTypes();
    }

    @GetMapping("/ai/status")
    public AiModelRegistry.AiRuntimeStatus aiStatus() {
        return modelRegistry.status();
    }
}
