package com.sakhtyar.quality;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Synthetic, in-memory lifecycle contract harness. The 24 production fixture factories
 * are still fail-closed. This class is NOT a durable JDBC fixture journal or E2E PASS.
 * No database writes, external effects, or wildcard cleanup are possible here.
 */
public final class Phase02BlockedFixtureLifecycle {
    public enum Action { PREPARE, SNAPSHOT, TEARDOWN }
    public record Event(FixtureContext fixture, Action action, String state,
                        Instant capturedAt, String safetyReason) {}
    private final Phase02FixtureFactories factories;
    private final Clock clock;
    private final Map<FixtureContext, Event> latest = new ConcurrentHashMap<>();

    public Phase02BlockedFixtureLifecycle() {
        this(new Phase02FixtureFactories(), Clock.systemUTC());
    }
    public Phase02BlockedFixtureLifecycle(Phase02FixtureFactories factories, Clock clock) {
        this.factories = Objects.requireNonNull(factories);
        this.clock = Objects.requireNonNull(clock);
    }

    public Event perform(Action action, UUID runId, UUID workspaceId, String scenarioId) {
        Objects.requireNonNull(action, "Lifecycle action required");
        Phase02FixtureFactory factory = factories.get(scenarioId);
        UUID fixtureId = Phase02FixtureKeys.fixtureId(workspaceId, runId, scenarioId, "root");
        FixtureContext context = new FixtureContext(workspaceId, runId, fixtureId, scenarioId);
        Phase02FixtureFactory.FixtureOutcome outcome = switch (action) {
            case PREPARE -> factory.prepare(runId, workspaceId);
            case SNAPSHOT -> factory.snapshot(runId, workspaceId);
            case TEARDOWN -> factory.teardown(runId, workspaceId);
        };
        if (outcome == null || !"BLOCKED".equals(outcome.state()) ||
            !fixtureId.equals(outcome.deterministicFixtureId()) ||
            !"false".equals(outcome.auditLabels().get("writePerformed")))
            throw new SecurityException("Unverified lifecycle claimed a fixture write/pass");
        Event observed = new Event(context, action, "BLOCKED", clock.instant(), outcome.reason());
        latest.put(context, observed);
        return observed;
    }

    public Event last(FixtureContext fixture) { return latest.get(Objects.requireNonNull(fixture)); }
}
