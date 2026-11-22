package com.sakhtyar.quality.phase2;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class Phase2OfflineContractsTest {
    private static final List<String> IDS=List.of("E2E-07","E2E-08","E2E-09","E2E-10",
        "E2E-11","E2E-12","E2E-13","E2E-15","E2E-16","E2E-17",
        "E2E-18","E2E-19","E2E-20");
    @Test void allThirteenDomainProbesExecuteWithoutExternalEffects() {
        for(String id:IDS) {
            var result=Phase2OfflineContracts.exercise(id);
            assertTrue(result.passed(),id+" "+result.observations());
            assertEquals(false,result.observations().get("businessWorkflowExecuted"));
            assertEquals(0,result.observations().get("sideEffects"));
        }
    }
    @Test void crawlerDenyContractRejectsPrivilegedTargets() {
        for(String url:List.of("http://localhost","file:///tmp/foo","https://127.0.0.1",
                "https://10.0.0.1","https://user@example.org","https://example.org:443"))
            assertFalse(Phase2OfflineContracts.safeCrawlerTarget(url),url);
    }
    @Test void everyScenarioHasNegativePlanAndUnmetAcceptanceEvidenceFailsClosed() {
        assertEquals(13,Phase2ScenarioSpecification.all().size());
        for(String id:IDS) {
            var definition=Phase2ScenarioSpecification.get(id);
            assertFalse(definition.fixture().isBlank());
            assertFalse(definition.negative().isBlank());
            assertFalse(definition.expected().isBlank());
            assertEquals(7,definition.requiredEvidence().size());
        }
        assertFalse(Phase2ScenarioSpecification.acceptanceEvidenceComplete(java.util.Map.of()));
    }
    @Test void invalidScenarioNeverPasses() {
        var result=Phase2OfflineContracts.exercise("E2E-21");
        assertFalse(result.passed());
        assertEquals(false,result.observations().get("businessWorkflowExecuted"));
    }
}
