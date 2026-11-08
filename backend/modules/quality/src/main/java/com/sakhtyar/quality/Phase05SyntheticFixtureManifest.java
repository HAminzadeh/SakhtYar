package com.sakhtyar.quality;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Identifiers for Phase05 E2E-15..20 synthetic fixtures, not persisted fixtures.
 * Preparing/cleaning live data must remain BLOCKED until restricted DB principals
 * and workspace isolation are independently proven.
 */
public final class Phase05SyntheticFixtureManifest {
    private Phase05SyntheticFixtureManifest() {}

    public record Plan(UUID workspaceId, UUID runId, String scenarioId,
                       Map<String,UUID> resourceIds, String executionState,
                       String cleanupPolicy) {
        public Plan {
            Objects.requireNonNull(workspaceId);
            Objects.requireNonNull(runId);
            resourceIds=Map.copyOf(resourceIds);
            if(!"BLOCKED".equals(executionState))
                throw new IllegalArgumentException("Phase05 fixture writes are not enabled.");
            if(!"NO_LIVE_CLEANUP".equals(cleanupPolicy))
                throw new IllegalArgumentException("Live cleanup must remain disabled.");
        }
    }

    public static Plan plan(UUID workspace, UUID run, String scenarioId) {
        if(workspace==null || run==null || scenarioId==null ||
                !scenarioId.matches("E2E-(1[5-9]|20)"))
            throw new IllegalArgumentException("Phase05 requires workspace, run and E2E-15..20.");
        Map<String,UUID> ids=new LinkedHashMap<>();
        for(String kind : new String[]{"builder","property","owner_a","owner_b","material",
                "price_observation","assembly","scenario","finance_participant",
                "finance_analysis","feasibility","sensitivity","snapshot"}) {
            ids.put(kind, Phase02FixtureKeys.fixtureId(workspace,run,scenarioId,kind));
        }
        return new Plan(workspace,run,scenarioId,ids,"BLOCKED","NO_LIVE_CLEANUP");
    }

    public static void requireNoWrites(Plan plan) {
        Objects.requireNonNull(plan,"plan");
        throw new SecurityException("Phase05 real fixture seed/cleanup requires verified Phase02 isolation; BLOCKED");
    }
}
