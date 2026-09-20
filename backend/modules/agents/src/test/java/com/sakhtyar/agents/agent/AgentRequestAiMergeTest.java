package com.sakhtyar.agents.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sakhtyar.agents.core.AgentRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentRequestAiMergeTest {

    @Test
    void explicitParametersMustWinOverAiExtraction() {
        AgentRequest request = new AgentRequest(
                null,
                null,
                null,
                "test",
                Map.of("coverageRatio", 60)
        );

        AgentRequest merged = request.mergeMissingParameters(
                Map.of(
                        "coverageRatio", 70,
                        "allowedResidentialFloors", 5
                )
        );

        assertEquals(60, merged.parameters().get("coverageRatio"));
        assertEquals(5, merged.parameters().get("allowedResidentialFloors"));
    }
}
