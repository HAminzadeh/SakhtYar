package com.sakhtyar.agents.orchestrator;

import com.sakhtyar.agents.core.AgentContracts;
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
        String schemaVersion,
        UUID requestId,
        UUID conversationId,
        UUID caseId,
        AgentIntent intent,
        AgentWorkflowType workflow,
        AgentStatus status,
        String message,
        Map<String, Object> inputs,
        List<String> assumptions,
        Map<String, AgentResult> results,
        List<String> missingFields
) {
    public AgentWorkflowResult {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank()
                ? AgentContracts.SCHEMA_VERSION
                : schemaVersion;
        inputs = Collections.unmodifiableMap(
                new LinkedHashMap<>(inputs == null ? Map.of() : inputs)
        );
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        results = Collections.unmodifiableMap(
                new LinkedHashMap<>(results == null ? Map.of() : results)
        );
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
    }
}
