package com.sakhtyar.shared.contract;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Real JSON serialization and Persian/Unicode regression vectors for the existing v2 envelope. */
class Phase04CanonicalContractsTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void rejectsNonFiniteConfidenceBeforeJsonSerialization() {
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> response(value));
        }
    }

    @Test void preservesFiniteClampingAndLegacyFieldNames() throws Exception {
        var low = response(-0.25);
        var high = response(1.25);
        assertEquals(0.0, low.confidence());
        assertEquals(1.0, high.confidence());
        JsonNode result = JSON.readTree(JSON.writeValueAsString(high));
        assertEquals("2.0", result.get("schemaVersion").asText());
        assertEquals("SUCCESS", result.get("status").asText());
        assertEquals(1.0, result.get("confidence").asDouble());
        assertTrue(result.has("missingFields"));
        assertTrue(result.has("completedAt"));
    }

    @Test void preservesPersianZwnjAndExactDecimalInRequestSerialization() throws Exception {
        String persian = "ساخت\u200cیار، ملک تهران";
        BigDecimal exact = new BigDecimal("123456789012345.123456789");
        UUID id = UUID.fromString("23456789-1234-4123-8123-123456789012");
        Instant time = Instant.parse("2026-11-22T01:02:03Z");
        var request = new CanonicalAgentRequest("2.0", id, id, null,
                "  REGULATION_SEARCH  ", Map.of("prompt", persian),
                Map.of("price", exact), Map.of(), time);
        String serialized = JSON.writeValueAsString(request);
        JsonNode result = JSON.readTree(serialized);
        assertEquals("REGULATION_SEARCH", result.get("intent").asText());
        assertEquals(persian, result.get("context").get("prompt").asText());
        // Assert wire-format precision without relying on generic JSON tree float settings.
        assertTrue(serialized.contains(exact.toPlainString()));
        assertEquals(time, Instant.parse(result.get("createdAt").asText()));
        assertTrue(result.get("projectId").isNull());
    }

    @Test void snapshotsTopLevelInputMapsAndKeepsOriginalDefaults() {
        Map<String,Object> input = new LinkedHashMap<>();
        input.put("label", "پارسی");
        var request = new CanonicalAgentRequest(null, null, null, null, null,
                input, null, null, null);
        input.put("label", "changed");
        assertEquals("پارسی", request.context().get("label"));
        assertThrows(UnsupportedOperationException.class, () -> request.context().put("x", 5));
        assertEquals(SchemaVersions.AGENT_ENVELOPE_V2, request.schemaVersion());
        assertEquals(request.requestId(), request.correlationId());
        assertEquals("UNKNOWN", request.intent());
        assertNotNull(request.createdAt());
    }

    @Test void responsePreservesWarningAndMetadataCollections() {
        var value = response(0.75);
        assertEquals(List.of("توجه"), value.warnings());
        assertThrows(UnsupportedOperationException.class, () -> value.warnings().add("extra"));
        assertThrows(UnsupportedOperationException.class, () -> value.data().put("x", 42));
    }

    private static CanonicalAgentResponse response(double confidence) {
        return new CanonicalAgentResponse("2.0", null, null, "SUCCESS",
                Map.of("value", 12), List.of("توجه"), List.of(), confidence,
                Map.of("language", "fa-IR"), Instant.parse("2026-11-22T01:02:03Z"));
    }
}
