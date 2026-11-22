package com.sakhtyar.quality;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SimulationCatalogContractTest {
    @Test
    void catalogHasExactly24DistinctCasesAndNeverClaimsBusinessWrites() throws Exception {
        var bytes = new ClassPathResource("simulation/scenarios.json").getInputStream().readAllBytes();
        JsonNode root = new ObjectMapper().readTree(new String(bytes, StandardCharsets.UTF_8));
        assertEquals(24, root.size());
        Set<String> ids = new HashSet<>();
        for(JsonNode item : root) {
            assertTrue(ids.add(item.path("id").asText()));
            assertTrue(item.path("id").asText().matches("E2E-[0-9]{2}"));
            assertEquals("BLOCKED_UNTIL_VERIFIED_ISOLATION",item.path("execution").asText());
            assertFalse(item.path("writesBusinessData").asBoolean());
        }
    }
}
