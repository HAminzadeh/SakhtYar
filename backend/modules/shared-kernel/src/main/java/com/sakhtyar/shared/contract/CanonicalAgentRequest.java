package com.sakhtyar.shared.contract;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record CanonicalAgentRequest(
        String schemaVersion,
        UUID requestId,
        UUID correlationId,
        UUID projectId,
        String intent,
        Map<String, Object> context,
        Map<String, Object> data,
        Map<String, Object> metadata,
        Instant createdAt
) {
    public CanonicalAgentRequest {
        schemaVersion = normalize(schemaVersion, SchemaVersions.AGENT_ENVELOPE_V2);
        requestId = requestId == null ? UUID.randomUUID() : requestId;
        correlationId = correlationId == null ? requestId : correlationId;
        intent = normalize(intent, "UNKNOWN");
        context = immutable(context);
        data = immutable(data);
        metadata = immutable(metadata);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static Map<String, Object> immutable(Map<String, Object> value) {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(value == null ? Map.of() : value)
        );
    }
}