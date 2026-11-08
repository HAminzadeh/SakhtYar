package com.sakhtyar.agents.provider;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase06FakeAiModelProviderTest {
    private static Phase06FakeAiModelProvider fake(Phase06FakeAiModelProvider.Failure failure) {
        return new Phase06FakeAiModelProvider(UUID.fromString("00000000-0000-4000-8000-000000000111"),
                UUID.fromString("00000000-0000-4000-8000-000000000222"), failure);
    }
    private static AiModelProvider.AiModelRequest request(String text) {
        return new AiModelProvider.AiModelRequest("private system instruction", text, Map.of());
    }
    @Test void deterministicNoPromptOrSecretInOutput() {
        var f = fake(Phase06FakeAiModelProvider.Failure.NONE);
        var first = f.generate(request("Bearer sensitive-example"));
        var second = f.generate(request("Bearer sensitive-example"));
        assertEquals(first, second);
        assertFalse(first.text().contains("Bearer"));
        assertFalse(first.structuredData().toString().contains("sensitive-example"));
        assertEquals(0, first.inputTokens());
    }
    @Test void rejectToolInjectionAndUnknownTools() {
        var f = fake(Phase06FakeAiModelProvider.Failure.NONE);
        assertThrows(SecurityException.class, () -> f.runTool("execute_shell", Map.of("cmd", "rm -rf"), false));
        assertThrows(SecurityException.class, () -> f.runTool("read_only_summary", Map.of("query", "https://secret.example"), false));
        assertThrows(IllegalStateException.class, () -> f.runTool("read_only_summary", Map.of(), true));
        assertEquals(0, f.runTool("synthetic_estimate", Map.of("area", 200), false).get("externalEffects"));
    }
    @Test void boundedRetriesAndCircuitOpenWithoutNetwork() {
        var transientFake = fake(Phase06FakeAiModelProvider.Failure.TRANSIENT_ONCE);
        assertEquals("SYNTHETIC_RESPONSE", transientFake.generate(request("safe")).text());
        assertEquals(2, transientFake.attemptCount());
        var timeout = fake(Phase06FakeAiModelProvider.Failure.TIMEOUT);
        assertThrows(IllegalStateException.class, () -> timeout.generate(request("safe")));
        assertFalse(timeout.available());
        assertThrows(IllegalStateException.class, () -> timeout.generate(request("safe")));
    }
    @Test void invalidSchemaAndOverlongRequestFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> fake(Phase06FakeAiModelProvider.Failure.INVALID_SCHEMA).generate(request("ok")));
        assertThrows(IllegalArgumentException.class, () -> fake(Phase06FakeAiModelProvider.Failure.NONE).generate(request("x".repeat(4097))));
    }
}
