package com.sakhtyar.quality;

import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Reuses the existing Phase02 TEST boundary. No database or network operation. */
class Phase06ReusePhase02GuardTest {
    @Test void realProvidersRemainDeniedAndOnlyExplicitLoopbackMocksAllowed() {
        assertThrows(SecurityException.class,()->Phase02OutboundGuard.denyActualOutbound(Phase02OutboundGuard.Effect.AI));
        assertThrows(SecurityException.class,()->Phase02OutboundGuard.requireLocalMockEndpoint(
                Phase02OutboundGuard.Effect.WEBHOOK,URI.create("http://127.0.0.1:18080"),false));
        assertThrows(SecurityException.class,()->Phase02OutboundGuard.requireLocalMockEndpoint(
                Phase02OutboundGuard.Effect.WEBHOOK,URI.create("http://localhost:18080"),true));
        assertDoesNotThrow(()->Phase02OutboundGuard.requireLocalMockEndpoint(
                Phase02OutboundGuard.Effect.WEBHOOK,URI.create("http://127.0.0.1:18080"),true));
    }
    @Test void runAndWorkspaceFixturesAreNamespacedWithoutWrites() {
        UUID workspace=UUID.randomUUID(), a=UUID.randomUUID(), b=UUID.randomUUID();
        assertNotEquals(Phase02FixtureKeys.fixtureId(workspace,a,"E2E-21","root"),
                Phase02FixtureKeys.fixtureId(workspace,b,"E2E-21","root"));
        var factory=new Phase02FixtureFactories().get("E2E-21");
        assertEquals("BLOCKED",factory.prepare(a,workspace).state());
        assertEquals("BLOCKED",factory.teardown(a,workspace).state());
    }
}
