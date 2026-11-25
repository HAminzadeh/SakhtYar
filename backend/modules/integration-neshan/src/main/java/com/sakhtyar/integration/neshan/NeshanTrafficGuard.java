package com.sakhtyar.integration.neshan;

import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Per-process, non-blocking request budget and circuit breaker.
 * A request is consumed once, there are NO automatic retries and NO location cache.
 * Distributed deployments must add an upstream shared quota if globally necessary.
 */
@Component
public final class NeshanTrafficGuard {
    private static final long WINDOW_NANOS = 60_000_000_000L;
    private final LongSupplier monotonicNanos;
    private final int maxRequests;
    private final int failureThreshold;
    private final long openNanos;
    private long windowStart = Long.MIN_VALUE;
    private long openUntil = Long.MIN_VALUE;
    private int requests;
    private int consecutiveFailures;

    @Autowired
    public NeshanTrafficGuard(NeshanProperties properties) {
        this(properties, System::nanoTime);
    }

    NeshanTrafficGuard(NeshanProperties properties, LongSupplier monotonicNanos) {
        this.monotonicNanos = monotonicNanos;
        this.maxRequests = properties == null ? 60 : properties.effectiveRequestsPerMinute();
        this.failureThreshold = properties == null ? 5 : properties.effectiveFailureThreshold();
        this.openNanos = (properties == null ? 30L : properties.effectiveOpenSeconds()) * 1_000_000_000L;
    }

    public synchronized void beforeRequest() {
        long now = monotonicNanos.getAsLong();
        if (openUntil != Long.MIN_VALUE) {
            if (now < openUntil) throw blocked("Neshan circuit temporarily open.");
            openUntil = Long.MIN_VALUE;
            consecutiveFailures = 0;
        }
        if (windowStart == Long.MIN_VALUE || now < windowStart || now - windowStart >= WINDOW_NANOS) {
            windowStart = now;
            requests = 0;
        }
        if (requests >= maxRequests) throw blocked("Neshan local request limit reached.");
        requests++;
    }

    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
    }

    public synchronized void recordFailure() {
        if (++consecutiveFailures >= failureThreshold) {
            long now = monotonicNanos.getAsLong();
            // openNanos is bounded to 3600s, safe from overflow under realistic monotonic clocks.
            openUntil = now > Long.MAX_VALUE - openNanos ? Long.MAX_VALUE : now + openNanos;
        }
    }

    private static ResponseStatusException blocked(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
