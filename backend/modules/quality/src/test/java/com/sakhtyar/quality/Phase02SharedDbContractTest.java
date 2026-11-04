package com.sakhtyar.quality;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase02SharedDbContractTest {
    @Test void allNonCoreContractsAreSharedDbAndStillBlocked() {
        var factories = new Phase02FixtureFactories();
        assertEquals(24,factories.size());
        for (var contract: Phase02FixtureCatalog.all()) {
            assertFalse(contract.writesEnabled());
            if (!"E2E-14".equals(contract.scenarioId())) {
                assertEquals(Phase02FixtureContract.IsolationMode.SHARED_DB_TEST_GATED,
                    contract.isolationMode());
            }
            var result=factories.get(contract.scenarioId())
                .prepare(UUID.randomUUID(), UUID.randomUUID());
            assertEquals("BLOCKED",result.state());
            assertEquals("false",result.auditLabels().get("writePerformed"));
            assertEquals("0",result.auditLabels().get("externalEffects"));
        }
    }
}
