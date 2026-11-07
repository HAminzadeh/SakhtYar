package com.sakhtyar.quality;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Context-bound fake effect recorder for synthetic testing. Does not send HTTP, SMS,
 * email, webhook or provider requests. This context is NOT a DB authentication proof.
 * Uninstrumented production adapters remain a release blocker.
 */
public final class Phase02FakeEffects {
    private static final ThreadLocal<FixtureContext> CURRENT = new ThreadLocal<>();
    private final Phase02OutboundGuard.FakeSink sink = new Phase02OutboundGuard.FakeSink();

    public static void withFixture(FixtureContext context, Runnable task) {
        Objects.requireNonNull(context, "TEST context required");
        Objects.requireNonNull(task, "Task required");
        if (CURRENT.get() != null) throw new SecurityException("Nested TEST context denied");
        CURRENT.set(context);
        try { task.run(); } finally { CURRENT.remove(); }
    }

    /** Explicit context propagation for instrumented workers; does not trust ambient GUCs. */
    public static Runnable wrap(FixtureContext context, Runnable task) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(task);
        return () -> withFixture(context, task);
    }

    public void capture(FixtureContext context, Phase02OutboundGuard.Effect effect) {
        requireExactContext(context);
        sink.capture(effect);
    }

    public void authorizeLocalMock(FixtureContext context, Phase02OutboundGuard.Effect effect,
                                   URI endpoint, boolean explicitlyEnabled) {
        requireExactContext(context);
        Phase02OutboundGuard.requireLocalMockEndpoint(effect, endpoint, explicitlyEnabled);
        // Approval is metadata-only. No network client/redirect handler is invoked here.
    }

    public int count(Phase02OutboundGuard.Effect effect) { return sink.count(effect); }

    private static void requireExactContext(FixtureContext expected) {
        if (expected == null || CURRENT.get() == null || !expected.equals(CURRENT.get()))
            throw new SecurityException("Missing or foreign TEST effect context");
    }
}
