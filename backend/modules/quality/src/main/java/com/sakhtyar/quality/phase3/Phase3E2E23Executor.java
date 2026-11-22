package com.sakhtyar.quality.phase3;

import org.springframework.stereotype.Component;

/** Scenario-specific offline validation; full business evidence is not available. */
@Component
public final class Phase3E2E23Executor extends Phase3OfflineScenarioExecutor {
    @Override public String scenarioId() { return "E2E-23"; }
}
