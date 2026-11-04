package com.sakhtyar.quality;

import java.util.List;
import java.util.Objects;

/** Declared fixture requirements only; a contract is never permission to write business data. */
public record Phase02FixtureContract(
        String scenarioId,
        String phase,
        List<String> requiredTables,
        IsolationMode isolationMode,
        String expectedFixture,
        String cleanupStrategy,
        boolean writesEnabled) {
    public enum IsolationMode { CLONE_REQUIRED, LIMITED_CORE_RESTRICTED_LOGIN }

    public Phase02FixtureContract {
        Objects.requireNonNull(scenarioId);
        Objects.requireNonNull(phase);
        Objects.requireNonNull(requiredTables);
        Objects.requireNonNull(isolationMode);
        Objects.requireNonNull(expectedFixture);
        Objects.requireNonNull(cleanupStrategy);
        if (!scenarioId.matches("E2E-(0[1-9]|1[0-9]|2[0-4])"))
            throw new IllegalArgumentException("Unknown scenario format");
        requiredTables = List.copyOf(requiredTables);
        // Phase 02 creates no NEW business writer. Existing limited E2E-14 remains separate.
        if (writesEnabled) throw new IllegalArgumentException("A fixture contract cannot authorize writes");
    }
}
