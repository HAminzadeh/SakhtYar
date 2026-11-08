package com.sakhtyar.quality;

import org.springframework.stereotype.Component;

/** E2E-03: independently executable, safe diagnostic; NOT a full business E2E. */
@Component
public final class Phase07E2E03Executor extends Phase07BaseDiagnosticExecutor {
    public Phase07E2E03Executor() {
        super("E2E-03", "KNOWLEDGE_RBAC",
            "Admin/non-admin authenticated knowledge route and negative authorization", Phase02OutboundGuard.Effect.MINIO);
    }
}
