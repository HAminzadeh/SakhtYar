package com.sakhtyar.notification.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase06FakeNotificationOutboxTest {
    private final UUID workspace=UUID.fromString("00000000-0000-4000-8000-000000000111");
    private final UUID run=UUID.fromString("00000000-0000-4000-8000-000000000222");
    private static final class AdjustableClock extends Clock {
        private Instant time;
        AdjustableClock(Instant start) {time=start;}
        void advance(Duration duration) {time=time.plus(duration);}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return time;}
    }
    @Test void dedupRetryCooldownAndScopedTeardown() {
        var now=Instant.parse("2026-11-08T00:00:00Z");
        var clock=new AdjustableClock(now);
        var o=new Phase06FakeNotificationOutbox(clock,Duration.ofMinutes(5));
        assertEquals(o.enqueue(workspace,run,"price","FAKE_SMS"),o.enqueue(workspace,run,"price","FAKE_SMS"));
        assertEquals("FAILED",o.attempt(workspace,run,"price",true).status());
        assertEquals(1,o.attempt(workspace,run,"price",false).attempts());
        clock.advance(Duration.ofMinutes(6));
        assertEquals("SENT",o.attempt(workspace,run,"price",false).status());
        assertEquals(2,o.attempt(workspace,run,"price",false).attempts());
        var otherRun=UUID.fromString("00000000-0000-4000-8000-000000000333");
        o.enqueue(workspace,otherRun,"price","FAKE_EMAIL");
        assertEquals(1,o.teardown(workspace,run));
        assertEquals(1,o.size());
        assertEquals("SENT",o.attempt(workspace,otherRun,"price",false).status());
    }
    @Test void noRealChannelOrMalformedWorkspaceAccepted() {
        var o=new Phase06FakeNotificationOutbox(Clock.systemUTC(),Duration.ZERO);
        assertThrows(SecurityException.class,()->o.enqueue(workspace,run,"id","SMS"));
        assertThrows(IllegalArgumentException.class,()->o.enqueue(workspace,workspace,"id","FAKE_SMS"));
        assertEquals(0,o.size());
    }
}
