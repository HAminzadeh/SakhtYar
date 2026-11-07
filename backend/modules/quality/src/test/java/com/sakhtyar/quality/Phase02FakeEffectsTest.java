package com.sakhtyar.quality;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class Phase02FakeEffectsTest {
    private static FixtureContext context() {
        UUID w=UUID.randomUUID(),r=UUID.randomUUID();
        return new FixtureContext(w,r,Phase02FixtureKeys.fixtureId(w,r,"E2E-05","root"),"E2E-05");
    }
    @Test void recordsOnlyExplicitSyntheticSideEffect() {
        var fixture=context();var sink=new Phase02FakeEffects();
        Phase02FakeEffects.withFixture(fixture,()->{
            sink.authorizeLocalMock(fixture,Phase02OutboundGuard.Effect.WEBHOOK,
                URI.create("http://127.0.0.1:8089/mock"),true);
            sink.capture(fixture,Phase02OutboundGuard.Effect.WEBHOOK);
        });
        assertEquals(1,sink.count(Phase02OutboundGuard.Effect.WEBHOOK));
    }
    @Test void missingAndForeignContextAlwaysRejected() {
        var owner=context();var other=context();var sink=new Phase02FakeEffects();
        assertThrows(SecurityException.class,()->sink.capture(owner,Phase02OutboundGuard.Effect.EMAIL));
        Phase02FakeEffects.withFixture(owner,()->{
            assertThrows(SecurityException.class,()->sink.capture(other,Phase02OutboundGuard.Effect.EMAIL));
            assertThrows(SecurityException.class,()->Phase02FakeEffects.withFixture(other,()->{}));
            assertThrows(SecurityException.class,()->sink.authorizeLocalMock(owner,
                Phase02OutboundGuard.Effect.CRAWLER, URI.create("http://example.com/"),true));
            assertThrows(SecurityException.class,()->sink.authorizeLocalMock(owner,
                Phase02OutboundGuard.Effect.CRAWLER,
                URI.create("http://127.0.0.1:8089/mock?token=synthetic"),true));
        });
        assertEquals(0,sink.count(Phase02OutboundGuard.Effect.EMAIL));
    }
    @Test void workerRequiresExplicitPropagationAndClearsContext() throws Exception {
        var owner=context();var sink=new Phase02FakeEffects();
        var failure=new AtomicReference<Throwable>();
        Thread missing=new Thread(()->{try{sink.capture(owner,Phase02OutboundGuard.Effect.AI);}catch(Throwable t){failure.set(t);}});
        missing.start();missing.join();
        assertInstanceOf(SecurityException.class,failure.get());
        Thread authorized=new Thread(Phase02FakeEffects.wrap(owner,
            ()->sink.capture(owner,Phase02OutboundGuard.Effect.AI)));
        authorized.start();authorized.join();
        assertEquals(1,sink.count(Phase02OutboundGuard.Effect.AI));
        assertThrows(SecurityException.class,()->sink.capture(owner,Phase02OutboundGuard.Effect.AI));
    }
}
