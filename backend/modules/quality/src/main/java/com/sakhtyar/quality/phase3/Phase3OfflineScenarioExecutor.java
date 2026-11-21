package com.sakhtyar.quality.phase3;

import com.sakhtyar.quality.E2EExecutionContext;
import com.sakhtyar.quality.ScenarioExecutor;
import java.util.LinkedHashMap;
import java.util.Map;

/** Spring-registered offline oracle; immutable FAIL-CLOSED business result. */
public abstract class Phase3OfflineScenarioExecutor implements ScenarioExecutor {
    @Override public final String executionType() {return "OFFLINE_CONTRACT_ONLY";}
    @Override public final Outcome execute(E2EExecutionContext context) {
        if(context==null) return Outcome.blocked("Restricted E2E context missing");
        context.requireNotCancelled();
        Phase3ScenarioSpecification.Definition spec=Phase3ScenarioSpecification.get(scenarioId());
        Phase3OfflineContractEngine.Check check=Phase3OfflineContractEngine.exercise(scenarioId());
        Map<String,Object> proof=new LinkedHashMap<>(check.evidence());
        proof.put("scenarioId",scenarioId());proof.put("workspaceId",context.workspaceId().toString());
        proof.put("runId",context.runId().toString());proof.put("executionType",executionType());
        proof.put("dependencies",spec.dependencies());proof.put("negativePlan",spec.negative());
        proof.put("fixturePlan",spec.fixture());proof.put("requiredLiveEvidence",spec.requiredLiveEvidence());
        proof.put("offlineContractPassed",check.passed());proof.put("businessWorkflowExecuted",false);
        proof.put("positiveHttpAssertion",false);proof.put("negativeHttpAssertion",false);
        proof.put("dbAssertion",false);proof.put("browserAssertion",false);
        proof.put("externalCallsExecuted",0);
        if(!check.passed())return new Outcome("FAILED","Offline domain oracle failed",proof);
        return new Outcome("BLOCKED","Offline Phase3 oracle passed; full isolated authenticated HTTP/DB/browser evidence NOT VERIFIED",proof);
    }
}
