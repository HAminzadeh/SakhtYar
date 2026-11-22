package com.sakhtyar.quality;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase07DiagnosticExecutorsTest {
    private final List<Phase07DiagnosticExecutor> executors=List.of(
        new Phase07E2E01Executor(),
        new Phase07E2E02Executor(),
        new Phase07E2E03Executor(),
        new Phase07E2E04Executor(),
        new Phase07E2E05Executor(),
        new Phase07E2E06Executor(),
        new Phase07E2E07Executor(),
        new Phase07E2E08Executor(),
        new Phase07E2E09Executor(),
        new Phase07E2E10Executor(),
        new Phase07E2E11Executor(),
        new Phase07E2E12Executor(),
        new Phase07E2E13Executor(),
        new Phase07E2E14Executor(),
        new Phase07E2E15Executor(),
        new Phase07E2E16Executor(),
        new Phase07E2E17Executor(),
        new Phase07E2E18Executor(),
        new Phase07E2E19Executor(),
        new Phase07E2E20Executor(),
        new Phase07E2E21Executor(),
        new Phase07E2E22Executor(),
        new Phase07E2E23Executor(),
        new Phase07E2E24Executor());

    @Test void all24UniqueExecutorsHaveRealPhase02OfflineChecks() {
        assertEquals(24,executors.size());
        assertEquals(24,executors.stream().map(Phase07DiagnosticExecutor::scenarioId).distinct().count());
        UUID workspace=UUID.fromString("00000000-0000-4000-8000-000000000111");
        UUID run=UUID.fromString("00000000-0000-4000-8000-000000000222");
        for(Phase07DiagnosticExecutor executor:executors) {
            var proof=executor.execute(workspace,run);
            assertEquals("PASSED",proof.get("offlineContractStatus"),executor.scenarioId());
            assertEquals(false,proof.get("businessWorkflowExecuted"));
            assertEquals(0,proof.get("realNetworkCalls"));
            assertEquals(0,proof.get("realBusinessDataWrites"));
            assertEquals(true,proof.get("crossWorkspaceFakeStorageDenied"));
            assertNotNull(proof.get("unverifiedBusinessProof"));
        }
    }

    @Test void missingTestScopeFailsClosed() {
        for(Phase07DiagnosticExecutor executor:executors)
            assertThrows(IllegalArgumentException.class,()->executor.execute(null,UUID.randomUUID()));
    }
}
