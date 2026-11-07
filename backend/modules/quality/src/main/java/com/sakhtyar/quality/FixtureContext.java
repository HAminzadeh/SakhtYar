package com.sakhtyar.quality;

import java.util.UUID;

/** Explicit owner-scoped TEST fixture identity. No global cleanup contract is provided. */
public record FixtureContext(UUID workspaceId, UUID runId, UUID fixtureId, String scenarioId) {
    public FixtureContext {
        if (workspaceId == null || runId == null || fixtureId == null || scenarioId == null ||
            !scenarioId.matches("E2E-(0[1-9]|1[0-9]|2[0-4])")) {
            throw new IllegalArgumentException("Incomplete isolated TEST fixture context");
        }
    }
}
