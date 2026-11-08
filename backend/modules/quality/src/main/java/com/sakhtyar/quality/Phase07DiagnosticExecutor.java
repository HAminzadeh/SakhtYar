package com.sakhtyar.quality;

import java.util.Map;
import java.util.UUID;

/** One distinct, side-effect-free diagnostic executor per business scenario.
 * A diagnostic result is NEVER a full business E2E result. */
public interface Phase07DiagnosticExecutor {
    String scenarioId();
    Map<String, Object> execute(UUID workspaceId, UUID runId);
}
