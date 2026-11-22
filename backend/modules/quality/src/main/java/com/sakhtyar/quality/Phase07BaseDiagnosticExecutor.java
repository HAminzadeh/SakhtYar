package com.sakhtyar.quality;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Executable contracts against the existing Phase 02 fake TEST infrastructure.
 * None of the checks calls an HTTP client, a database mutator, a real provider, or a live sender.
 * A successfully executed diagnostic is not proof that the business workflow is implemented.
 */
public abstract class Phase07BaseDiagnosticExecutor implements Phase07DiagnosticExecutor {
    private final String id;
    private final String diagnosticFocus;
    private final String unverifiedBusinessProof;
    private final Phase02OutboundGuard.Effect effect;

    protected Phase07BaseDiagnosticExecutor(String id, String diagnosticFocus,
                                             String unverifiedBusinessProof,
                                             Phase02OutboundGuard.Effect effect) {
        this.id = id;
        this.diagnosticFocus = diagnosticFocus;
        this.unverifiedBusinessProof = unverifiedBusinessProof;
        this.effect = effect;
        SimulationScenarioPlans.get(id); // fail at startup if catalog and executor diverge
    }

    @Override public final String scenarioId() { return id; }

    @Override public final Map<String, Object> execute(UUID workspaceId, UUID runId) {
        if (workspaceId == null || runId == null) throw new IllegalArgumentException("TEST workspace and run required");
        Phase02FixtureContract fixture = Phase02FixtureCatalog.get(id);
        if (fixture.writesEnabled()) throw new SecurityException("Unexpected fixture write permission");
        UUID fixtureId = Phase02FixtureKeys.fixtureId(workspaceId, runId, id, "root");
        if (!fixtureId.equals(Phase02FixtureKeys.fixtureId(workspaceId, runId, id, "root")))
            throw new IllegalStateException("Fixture identity not deterministic");
        UUID foreignWorkspace = UUID.nameUUIDFromBytes(("phase07-other/" + workspaceId).getBytes(StandardCharsets.UTF_8));
        if (foreignWorkspace.equals(workspaceId) || fixtureId.equals(
                Phase02FixtureKeys.fixtureId(foreignWorkspace, runId, id, "root")))
            throw new SecurityException("Fixture identity is not workspace-isolated");

        Phase02SandboxNamespaces own = new Phase02SandboxNamespaces(workspaceId, runId);
        Phase02SandboxNamespaces foreign = new Phase02SandboxNamespaces(foreignWorkspace, runId);
        if (!own.ownsRedisKey(own.redisKey("canary")) || foreign.ownsRedisKey(own.redisKey("canary")) ||
            !own.ownsMinioKey(own.minioKey("canary")) || foreign.ownsMinioKey(own.minioKey("canary")))
            throw new SecurityException("TEST namespace separation failed");

        FixtureContext owner = new FixtureContext(workspaceId, runId, fixtureId, id);
        FixtureContext other = new FixtureContext(foreignWorkspace, runId,
            Phase02FixtureKeys.fixtureId(foreignWorkspace, runId, id, "root"), id);
        Phase02ScopedFakeStorage fake = new Phase02ScopedFakeStorage();
        fake.put(owner, Phase02ScopedFakeStorage.Service.REDIS, "phase07-canary",
            new byte[]{1, 2, 3}, Duration.ofMinutes(1));
        if (fake.read(owner, Phase02ScopedFakeStorage.Service.REDIS, "phase07-canary") == null ||
            fake.read(other, Phase02ScopedFakeStorage.Service.REDIS, "phase07-canary") != null ||
            fake.totalCanaryKeys() != 1)
            throw new SecurityException("In-memory TEST canary isolation failed");
        if (!fake.delete(owner, Phase02ScopedFakeStorage.Service.REDIS, "phase07-canary") ||
            fake.totalCanaryKeys() != 0)
            throw new SecurityException("Run-owned fake cleanup failed");

        boolean denied = false;
        try { Phase02OutboundGuard.requireLocalMockEndpoint(effect,
            URI.create("https://real-provider.example.invalid/api"), true); }
        catch (SecurityException expected) { denied = true; }
        if (!denied) throw new SecurityException("Unsafe external endpoint accepted");
        denied = false;
        try { Phase02OutboundGuard.denyActualOutbound(effect); }
        catch (SecurityException expected) { denied = true; }
        if (!denied) throw new SecurityException("Actual outbound was not denied");

        Map<String,Object> answer = new LinkedHashMap<>();
        answer.put("scenarioId", id);
        answer.put("diagnosticFocus", diagnosticFocus);
        answer.put("fixtureContract", fixture.isolationMode().name());
        answer.put("fixtureWritesAuthorized", false);
        answer.put("deterministicScopedFixture", true);
        answer.put("crossWorkspaceFakeStorageDenied", true);
        answer.put("fakeCanaryExactCleanup", true);
        answer.put("externalOutboundDenied", true);
        answer.put("effectClass", effect.name());
        answer.put("offlineContractStatus", "PASSED");
        answer.put("businessWorkflowExecuted", false);
        answer.put("realNetworkCalls", 0);
        answer.put("realBusinessDataWrites", 0);
        answer.put("unverifiedBusinessProof", unverifiedBusinessProof);
        answer.put("notes", List.of(
            "Phase 02 mock/namespace checks executed; real adapters and PostgreSQL RLS NOT thereby proven",
            "Full authenticated HTTP positive/negative, restricted-DB, browser and immutable artifacts are still required"));
        return Map.copyOf(answer);
    }
}
