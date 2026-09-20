package com.sakhtyar.agents.core;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AgentResult(
        AgentType agent,
        AgentStatus status,
        String message,
        Map<String, Object> data,
        List<String> warnings,
        List<String> missingFields,
        double confidence,
        Instant completedAt
) {
    public AgentResult {
        message = message == null ? "" : message;
        data = Collections.unmodifiableMap(
                new LinkedHashMap<>(data == null ? Map.of() : data)
        );
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        confidence = Math.max(0.0d, Math.min(1.0d, confidence));
        completedAt = completedAt == null ? Instant.now() : completedAt;
    }

    public static AgentResult failed(AgentType type, String message) {
        return new AgentResult(
                type,
                AgentStatus.FAILED,
                message,
                Map.of(),
                List.of(),
                List.of(),
                0.0d,
                Instant.now()
        );
    }

    public static AgentResult needsInput(
            AgentType type,
            String message,
            Map<String, Object> data,
            List<String> missingFields
    ) {
        return new AgentResult(
                type,
                AgentStatus.NEEDS_INPUT,
                message,
                data,
                List.of(),
                missingFields,
                0.0d,
                Instant.now()
        );
    }
}
