package com.sakhtyar.quality;

import java.net.URI;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase02OutboundGuardTest {
    @Test void externalEndpointsAndSsrfVariantsAreDenied() {
        for(String url:new String[]{"https://example.com/", "http://example.com/",
                "http://localhost:8089/", "http://127.0.0.1:80/",
                "http://127.0.0.1:8089@evil.example/", "file:///etc/passwd",
                "http://169.254.169.254:8089/latest/meta-data"}) {
            assertThrows(SecurityException.class,()->Phase02OutboundGuard.requireLocalMockEndpoint(
                Phase02OutboundGuard.Effect.CRAWLER,URI.create(url),true),url);
        }
        assertThrows(SecurityException.class,()->Phase02OutboundGuard.requireLocalMockEndpoint(
                Phase02OutboundGuard.Effect.AI,URI.create("http://127.0.0.1:8089/"),false));
        assertDoesNotThrow(()->Phase02OutboundGuard.requireLocalMockEndpoint(
                Phase02OutboundGuard.Effect.CRAWLER,URI.create("http://127.0.0.1:8089/mock"),true));
        assertThrows(SecurityException.class,()->Phase02OutboundGuard.denyActualOutbound(Phase02OutboundGuard.Effect.EMAIL));
    }
    @Test void fakeSinkNeverDispatchesOrStoresMessageBodies() {
        Phase02OutboundGuard.FakeSink sink=new Phase02OutboundGuard.FakeSink();
        sink.capture(Phase02OutboundGuard.Effect.EMAIL);
        assertEquals(1,sink.count(Phase02OutboundGuard.Effect.EMAIL));
        assertEquals(0,sink.count(Phase02OutboundGuard.Effect.WEBHOOK));
        assertFalse(sink.counts().toString().contains("@"));
    }
}
