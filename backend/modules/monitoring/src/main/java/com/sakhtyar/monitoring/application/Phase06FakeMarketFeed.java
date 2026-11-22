package com.sakhtyar.monitoring.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Synthetic, injected feed; never reads the production market database. */
public final class Phase06FakeMarketFeed {
    private final Clock clock;
    private final UUID workspaceId;
    private final UUID runId;
    public Phase06FakeMarketFeed(Clock clock, UUID workspaceId, UUID runId) {
        if(clock==null || workspaceId==null || runId==null || workspaceId.equals(runId))throw new IllegalArgumentException("TEST scope required");
        this.clock=clock;this.workspaceId=workspaceId;this.runId=runId;
    }
    public Map<String,Object> quote(String syntheticCode) {
        if(!"SYNTHETIC_IRR_M2".equals(syntheticCode)) throw new SecurityException("Live feed not available in TEST");
        return Map.of("code",syntheticCode,"medianIRR",new BigDecimal("10000000.00"),
                "sampleCount",5,"observedAt",Instant.now(clock).toString(),"workspace",workspaceId.toString(),
                "run",runId.toString(),"origin","SYNTHETIC_ONLY");
    }
}
