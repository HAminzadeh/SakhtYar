package com.sakhtyar.quality;

import org.springframework.stereotype.Component;

/** E2E-06: independently executable, safe diagnostic; NOT a full business E2E. */
@Component
public final class Phase07E2E06Executor extends Phase07BaseDiagnosticExecutor {
    public Phase07E2E06Executor() {
        super("E2E-06", "CSRF_NEGATIVE_HTTP",
            "CSRF header/cookie positive and negative requests with real sessions", Phase02OutboundGuard.Effect.PROVIDER);
    }
}
