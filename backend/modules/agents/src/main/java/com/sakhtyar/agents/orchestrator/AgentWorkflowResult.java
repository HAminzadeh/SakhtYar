package com.sakhtyar.agents.orchestrator;

import com.sakhtyar.agents.core.AgentIntent;
import com.sakhtyar.agents.core.AgentResult;
import com.sakhtyar.agents.core.AgentStatus;
import com.sakhtyar.agents.core.AgentWorkflowType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AgentWorkflowResult(
        UUID requestId,
        UUID conversationId,
        AgentIntent intent,
        AgentWorkflowType workflow,
        AgentStatus status,
        String message,
        Map<String, AgentResult> results,
        List<String> missingFields
) {
    public AgentWorkflowResult {
        results = Collections.unmodifiableMap(
                new LinkedHashMap<>(results == null ? Map.of() : results)
        );
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
    }
}
