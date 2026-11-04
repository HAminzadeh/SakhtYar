package com.sakhtyar.quality;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 24 per-scenario safe factories. Non-core domain fixture writes remain disabled by design. */
public final class Phase02FixtureFactories {
    private final Map<String,Phase02FixtureFactory> factories;
    public Phase02FixtureFactories() {
        Map<String,Phase02FixtureFactory> built=new LinkedHashMap<>();
        for (Phase02FixtureContract contract : Phase02FixtureCatalog.all())
            built.put(contract.scenarioId(),new DeniedUntilIsolatedFactory(contract));
        factories=Map.copyOf(built);
        if(factories.size()!=24) throw new IllegalStateException("Missing fixture contract");
    }
    public Phase02FixtureFactory get(String scenarioId) {
        Phase02FixtureFactory factory=factories.get(scenarioId);
        if(factory==null) throw new IllegalArgumentException("Unknown scenario");
        return factory;
    }
    public int size() { return factories.size(); }

    private record DeniedUntilIsolatedFactory(Phase02FixtureContract contract) implements Phase02FixtureFactory {
        private FixtureOutcome deny(UUID run, UUID workspace, String action) {
            UUID fixture=Phase02FixtureKeys.fixtureId(workspace,run,contract.scenarioId(),"root");
            String reason="No verified per-scenario isolated " + action +
                " implementation; do not mutate REAL or shared services";
            return new FixtureOutcome("BLOCKED",reason,fixture,Map.of(
                "scenario",contract.scenarioId(), "isolation",contract.isolationMode().name(),
                "writePerformed","false", "externalEffects","0"));
        }
        @Override public FixtureOutcome prepare(UUID run, UUID workspace) { return deny(run,workspace,"prepare"); }
        @Override public FixtureOutcome snapshot(UUID run, UUID workspace) { return deny(run,workspace,"snapshot"); }
        @Override public FixtureOutcome teardown(UUID run, UUID workspace) { return deny(run,workspace,"teardown"); }
    }
}
