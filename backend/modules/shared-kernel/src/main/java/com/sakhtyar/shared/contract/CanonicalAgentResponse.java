package com.sakhtyar.shared.contract;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CanonicalAgentResponse(
        String schemaVersion,
        UUID requestId,
        UUID correlationId,
        String status,
        Map<String, Object> data,
        List<String> warnings,
        List<String> missingFields,
        double confidence,
        Map<String, Object> metadata,
        Instant completedAt
) {
    public CanonicalAgentResponse {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank()
                ? SchemaVersions.AGENT_ENVELOPE_V2
                : schemaVersion.trim();
        status = status == null || status.isBlank() ? "UNKNOWN" : status.trim();
        data = immutable(data);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        confidence = Math.max(0.0d, Math.min(1.0d, confidence));
        metadata = immutable(metadata);
        completedAt = completedAt == null ? Instant.now() : completedAt;
    }

    private static Map<String, Object> immutable(Map<String, Object> value) {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(value == null ? Map.of() : value)
        );
    }
}