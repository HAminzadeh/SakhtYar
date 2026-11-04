package com.sakhtyar.quality;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Contract inventory sourced from the committed 24-scenario plan, avoiding a divergent second catalog. */
public final class Phase02FixtureCatalog {
    private static final Map<String,Phase02FixtureContract> CONTRACTS = load();
    private Phase02FixtureCatalog() {}

    private static Map<String,Phase02FixtureContract> load() {
        Map<String,Phase02FixtureContract> result = new LinkedHashMap<>();
        for (SimulationScenarioPlans.Plan plan : SimulationScenarioPlans.all()) {
            boolean core = "E2E-14".equals(plan.id());
            Phase02FixtureContract contract = new Phase02FixtureContract(
                plan.id(), plan.phase(), plan.requiredTables(),
                core ? Phase02FixtureContract.IsolationMode.LIMITED_CORE_RESTRICTED_LOGIN
                     : Phase02FixtureContract.IsolationMode.CLONE_REQUIRED,
                core ? "Existing four-table synthetic case/property/two-owner fixture (LIMITED)"
                     : "Scenario-specific synthetic fixture, pending domain integration",
                core ? "NO_AUTO_TEARDOWN; verify immutable workspace/run before manual cleanup"
                     : "DISPOSABLE_CLONE_ONLY; no deletion from REAL or shared stores",
                false);
            if (result.putIfAbsent(contract.scenarioId(),contract)!=null)
                throw new IllegalStateException("Duplicate scenario fixture contract");
        }
        if (result.size()!=24) throw new IllegalStateException("Expected 24 fixture contracts");
        return Map.copyOf(result);
    }

    public static Collection<Phase02FixtureContract> all() {
        return CONTRACTS.values().stream().sorted(java.util.Comparator.comparing(Phase02FixtureContract::scenarioId)).toList();
    }
    public static Phase02FixtureContract get(String scenarioId) {
        Phase02FixtureContract found=CONTRACTS.get(scenarioId);
        if(found==null) throw new IllegalArgumentException("Unknown scenario ID");
        return found;
    }
}
