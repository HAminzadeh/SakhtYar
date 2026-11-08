package com.sakhtyar.agents.provider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Explicitly constructed, in-memory TEST double. Deliberately NOT a Spring bean:
 * production AiGateway continues to deny ROLE_TESTER, rather than silently routing
 * TEST requests to a REAL provider or database-backed model registry.
 * It neither connects to an external service nor retains/returns prompts.
 */
public final class Phase06FakeAiModelProvider implements AiModelProvider {
    public enum Failure { NONE, TRANSIENT_ONCE, TIMEOUT, INVALID_SCHEMA }
    private static final Set<String> TOOLS = Set.of("read_only_summary", "synthetic_estimate");
    private final UUID workspaceId;
    private final UUID runId;
    private final Failure failure;
    private int attempts;
    private int consecutiveFailures;

    public Phase06FakeAiModelProvider(UUID workspaceId, UUID runId, Failure failure) {
        if (workspaceId == null || runId == null || workspaceId.equals(runId) || failure == null)
            throw new IllegalArgumentException("Unique synthetic workspace/run and failure mode required");
        this.workspaceId = workspaceId;
        this.runId = runId;
        this.failure = failure;
    }

    @Override public String providerId() { return "phase06-fake-test-only"; }
    @Override public String modelId() { return "synthetic-1"; }
    @Override public boolean available() { return consecutiveFailures < 2; }

    @Override public synchronized AiModelResponse generate(AiModelRequest request) {
        if (!available()) throw new IllegalStateException("TEST_CIRCUIT_OPEN");
        if (request == null || request.userMessage() == null || request.userMessage().length() > 4096 ||
                request.structuredContext() == null || request.structuredContext().size() > 32)
            throw new IllegalArgumentException("Invalid bounded TEST model request");
        if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("TEST_CANCELLED");
        if (failure == Failure.INVALID_SCHEMA) throw new IllegalArgumentException("TEST_INVALID_OUTPUT_SCHEMA");
        for (int retry = 0; retry < 2; retry++) {
            attempts++;
            if (failure == Failure.TIMEOUT || (failure == Failure.TRANSIENT_ONCE && attempts == 1)) {
                consecutiveFailures++;
                if (failure == Failure.TIMEOUT) {
                    // No real waiting or external timeout; deterministic failure injection.
                    if (retry == 1) throw new IllegalStateException("TEST_PROVIDER_TIMEOUT");
                    continue;
                }
                continue;
            }
            consecutiveFailures = 0;
            String fingerprint = sha256(workspaceId + "/" + runId + "/" + request.userMessage());
            return new AiModelResponse("SYNTHETIC_RESPONSE", Map.of(
                    "intent", "RESEARCH", "parameters", Map.of(), "fingerprint", fingerprint,
                    "source", "SYNTHETIC_TEST_ONLY"), modelId(), 0, 0);
        }
        throw new IllegalStateException("TEST_PROVIDER_UNAVAILABLE");
    }

    public synchronized Map<String, Object> runTool(String toolId, Map<String, Object> args, boolean cancelled) {
        if (cancelled || Thread.currentThread().isInterrupted()) throw new IllegalStateException("TEST_CANCELLED");
        if (toolId == null || !TOOLS.contains(toolId)) throw new SecurityException("Tool not allowlisted");
        if (args == null || args.size() > 16 || args.keySet().stream().anyMatch(
                key -> key == null || !key.matches("[a-zA-Z][a-zA-Z0-9_]{0,31}")))
            throw new IllegalArgumentException("Unsafe TEST tool arguments");
        if (args.values().stream().anyMatch(v -> !(v instanceof Number || v instanceof Boolean ||
                (v instanceof String s && s.length() <= 80 && s.matches("[a-zA-Z0-9 _.-]*")))))
            throw new SecurityException("Untrusted tool values rejected");
        return Map.of("tool", toolId, "status", "SYNTHETIC_OK", "workspaceId", workspaceId.toString(),
                "runId", runId.toString(), "externalEffects", 0);
    }

    public int attemptCount() { return attempts; }
    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
}
