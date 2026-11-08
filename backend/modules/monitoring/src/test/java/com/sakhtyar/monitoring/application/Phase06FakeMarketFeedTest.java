package com.sakhtyar.monitoring.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase06FakeMarketFeedTest {
    @Test void syntheticAndFrozenNoRealMarketQueries() {
        var feed=new Phase06FakeMarketFeed(Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"),ZoneOffset.UTC),
                UUID.fromString("00000000-0000-4000-8000-000000000111"),
                UUID.fromString("00000000-0000-4000-8000-000000000222"));
        assertEquals("SYNTHETIC_ONLY",feed.quote("SYNTHETIC_IRR_M2").get("origin"));
        assertEquals("2026-11-08T00:00:00Z",feed.quote("SYNTHETIC_IRR_M2").get("observedAt"));
        assertThrows(SecurityException.class,()->feed.quote("REAL_MARKET_API"));
    }
}
