package com.sakhtyar.quality.phase2;

import com.sakhtyar.quality.E2EExecutionContext;
import com.sakhtyar.quality.ScenarioExecutor;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Offline, scenario-specific contract execution only. Never upgrades to FULL_BUSINESS_E2E,
 * does not connect to real providers and cannot authorize writes. The existing Phase08
 * controller pre-write gate remains mandatory. A successful offline check is BLOCKED,
 * not PASSED, until authenticated HTTP, restricted TEST DB, browser and artifacts exist.
 */
public abstract class Phase2OfflineScenarioExecutor implements ScenarioExecutor {
    @Override public final String executionType() { return "OFFLINE_CONTRACT_ONLY"; }
    @Override public final Outcome execute(E2EExecutionContext context) {
        if(context==null) return Outcome.blocked("Restricted E2E context missing");
        context.requireNotCancelled();
        Phase2ScenarioSpecification.Definition specification=Phase2ScenarioSpecification.get(scenarioId());
        Phase2OfflineContracts.Check check=Phase2OfflineContracts.exercise(scenarioId());
        Map<String,Object> evidence=new LinkedHashMap<>(check.observations());
        evidence.put("scenarioId",scenarioId());
        evidence.put("testPlanDomain",specification.domain());
        evidence.put("testPlanFixture",specification.fixture());
        evidence.put("testPlanNegative",specification.negative());
        evidence.put("testPlanExpected",specification.expected());
        evidence.put("requiredAcceptanceEvidence",specification.requiredEvidence());
        evidence.put("upstreamDependencies",specification.dependencies());
        evidence.put("workspaceId",context.workspaceId().toString());
        evidence.put("runId",context.runId().toString());
        evidence.put("executionType",executionType());
        evidence.put("offlineContractPassed",check.passed());
        evidence.put("businessWorkflowExecuted",false);
        evidence.put("positiveHttpAssertion",false);
        evidence.put("negativeHttpAssertion",false);
        evidence.put("dbAssertion",false);
        evidence.put("browserAssertion",false);
        evidence.put("fixtureWritesAuthorized",false);
        evidence.put("externalCallsExecuted",0);
        if(!check.passed()) return new Outcome("FAILED","Scenario-specific offline contract failed",evidence);
        return new Outcome("BLOCKED","Offline scenario contract passed; real authenticated HTTP, restricted TEST DB, browser and artifact proof missing",evidence);
    }
}
