package com.sakhtyar.quality;

import org.springframework.stereotype.Component;

/** E2E-21: independently executable, safe diagnostic; NOT a full business E2E. */
@Component
public final class Phase07E2E21Executor extends Phase07BaseDiagnosticExecutor {
    public Phase07E2E21Executor() {
        super("E2E-21", "AI_WORKFLOW",
            "TEST workflow and prompt injection, mock provider and output/permission evidence", Phase02OutboundGuard.Effect.AI);
    }
}
