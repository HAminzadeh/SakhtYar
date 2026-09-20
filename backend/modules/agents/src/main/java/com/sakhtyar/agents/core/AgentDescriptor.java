package com.sakhtyar.agents.core;

import java.util.List;
import java.util.Map;

public record AgentDescriptor(
        AgentType type,
        String name,
        String version,
        List<String> capabilities,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        List<String> requiredPermissions,
        int timeoutSeconds,
        String provider
) {
    public AgentDescriptor {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
        outputSchema = outputSchema == null ? Map.of() : Map.copyOf(outputSchema);
        requiredPermissions = requiredPermissions == null
                ? List.of()
                : List.copyOf(requiredPermissions);
    }
}
