package com.sakhtyar.quality;

import java.util.Map;
import java.util.UUID;

/** Every scenario receives an explicit factory; only a verified, separate executor may write. */
public interface Phase02FixtureFactory {
    Phase02FixtureContract contract();
    FixtureOutcome prepare(UUID runId, UUID workspaceId);
    FixtureOutcome snapshot(UUID runId, UUID workspaceId);
    FixtureOutcome teardown(UUID runId, UUID workspaceId);

    record FixtureOutcome(String state, String reason, UUID deterministicFixtureId,
                          Map<String,String> auditLabels) {
        public FixtureOutcome {
            if (!"BLOCKED".equals(state)) throw new IllegalArgumentException("Unverified fixture cannot pass");
            auditLabels=Map.copyOf(auditLabels);
        }
    }
}
