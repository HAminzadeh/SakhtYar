package com.sakhtyar.agents.api;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;

public final class AgentDtos {

    private AgentDtos() {
    }

    public record AgentChatRequest(
            UUID conversationId,
            UUID caseId,
            @NotBlank String message,
            Map<String, Object> parameters
    ) {
    }
}
