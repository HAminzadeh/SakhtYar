package com.sakhtyar.quality;

import org.springframework.stereotype.Component;

/** E2E-13: independently executable, safe diagnostic; NOT a full business E2E. */
@Component
public final class Phase07E2E13Executor extends Phase07BaseDiagnosticExecutor {
    public Phase07E2E13Executor() {
        super("E2E-13", "CRAWLER_LOCAL_MOCK",
            "Strict loopback TEST crawler with redirect, SSRF and dedup evidence", Phase02OutboundGuard.Effect.CRAWLER);
    }
}
