package com.sakhtyar.agents.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record AgentRequest(
        UUID requestId,
        UUID conversationId,
        UUID caseId,
        String message,
        Map<String, Object> parameters
) {
    public AgentRequest {
        requestId = requestId == null ? UUID.randomUUID() : requestId;
        conversationId = conversationId == null ? UUID.randomUUID() : conversationId;
        message = message == null ? "" : message.trim();
        parameters = Collections.unmodifiableMap(
                new LinkedHashMap<>(parameters == null ? Map.of() : parameters)
        );
    }

    /**
     * Existing behavior: values in extra override existing values.
     */
    public AgentRequest mergeParameters(Map<String, Object> extra) {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>(parameters);
        if (extra != null) {
            extra.forEach((key, value) -> {
                if (key != null && value != null) {
                    merged.put(key, value);
                }
            });
        }
        return new AgentRequest(requestId, conversationId, caseId, message, merged);
    }

    /**
     * Adds AI-extracted values only when the caller has not already supplied a
     * structured value. Explicit request data always has higher priority than an
     * LLM extraction.
     */
    public AgentRequest mergeMissingParameters(Map<String, Object> extra) {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>(parameters);
        if (extra != null) {
            extra.forEach((key, value) -> {
                if (key != null && value != null) {
                    merged.putIfAbsent(key, value);
                }
            });
        }
        return new AgentRequest(requestId, conversationId, caseId, message, merged);
    }
}
