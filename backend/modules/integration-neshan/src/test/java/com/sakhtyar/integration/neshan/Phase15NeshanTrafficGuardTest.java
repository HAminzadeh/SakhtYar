package com.sakhtyar.integration.neshan;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

/** All tests use a fake monotonic clock and never start an HTTP client. */
class Phase15NeshanTrafficGuardTest {
    private final AtomicLong clock = new AtomicLong(1_000_000_000L);

    private NeshanProperties configuration(int budget, int failThreshold, int openSeconds) {
        return new NeshanProperties(true, "https://api.neshan.org", "SYNTHETIC-NONSECRET", 2,
                true, budget, failThreshold, openSeconds);
    }

    @Test void allowsBudgetThenFailsClosedAndResetsAfterOneMinute() {
        var gate = new NeshanTrafficGuard(configuration(2, 5, 30), clock::get);
        gate.beforeRequest(); gate.recordSuccess();
        gate.beforeRequest(); gate.recordSuccess();
        ResponseStatusException exhausted = assertThrows(ResponseStatusException.class, gate::beforeRequest);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exhausted.getStatusCode());
        clock.addAndGet(60_000_000_000L);
        assertDoesNotThrow(gate::beforeRequest);
    }

    @Test void opensCircuitAfterTwoFailuresAndRecoversAfterCooldown() {
        var gate = new NeshanTrafficGuard(configuration(20, 2, 5), clock::get);
        gate.beforeRequest(); gate.recordFailure();
        gate.beforeRequest(); gate.recordFailure();
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ResponseStatusException.class, gate::beforeRequest).getStatusCode());
        clock.addAndGet(4_000_000_000L);
        assertThrows(ResponseStatusException.class, gate::beforeRequest);
        clock.addAndGet(1_000_000_000L);
        assertDoesNotThrow(gate::beforeRequest);
        gate.recordSuccess();
        assertDoesNotThrow(gate::beforeRequest);
    }

    @Test void successfulRequestClearsConsecutiveFailures() {
        var gate = new NeshanTrafficGuard(configuration(20, 2, 5), clock::get);
        gate.beforeRequest(); gate.recordFailure();
        gate.beforeRequest(); gate.recordSuccess();
        gate.beforeRequest(); gate.recordFailure();
        assertDoesNotThrow(gate::beforeRequest);
    }

    @Test void invalidConfigurationUsesBoundedNonzeroDefaults() {
        var props = configuration(0, -1, 0);
        assertEquals(60, props.effectiveRequestsPerMinute());
        assertEquals(5, props.effectiveFailureThreshold());
        assertEquals(30, props.effectiveOpenSeconds());
        var huge = configuration(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(600, huge.effectiveRequestsPerMinute());
        assertEquals(20, huge.effectiveFailureThreshold());
        assertEquals(3600, huge.effectiveOpenSeconds());
    }
}
