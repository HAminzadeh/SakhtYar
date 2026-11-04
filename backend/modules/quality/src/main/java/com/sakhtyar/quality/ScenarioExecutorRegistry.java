package com.sakhtyar.quality;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** A unique executor per scenario. Missing/unsupported implementations fail closed. */
@Component
public final class ScenarioExecutorRegistry {
    private final Map<String, ScenarioExecutor> executors;

    public ScenarioExecutorRegistry(List<ScenarioExecutor> provided) {
        Map<String, ScenarioExecutor> found = new LinkedHashMap<>();
        for (ScenarioExecutor executor : provided) {
            if (executor == null || !executor.scenarioId().matches("E2E-(0[1-9]|1[0-9]|2[0-4])")) {
                throw new IllegalStateException("Invalid scenario executor registration");
            }
            if (found.putIfAbsent(executor.scenarioId(), executor) != null) {
                throw new IllegalStateException("Duplicate executor for " + executor.scenarioId());
            }
        }
        executors = Map.copyOf(found);
    }

    public boolean supports(String id) {
        ScenarioExecutor executor = executors.get(id);
        return executor != null && executor.supports(id);
    }

    public Set<String> registeredIds() { return executors.keySet(); }

    public String executionType(String id) {
        ScenarioExecutor executor = executors.get(id);
        return executor == null || !executor.supports(id) ? "UNIMPLEMENTED_BUSINESS_WORKFLOW" : executor.executionType();
    }

    public ScenarioExecutor.Outcome execute(String id, E2EExecutionContext context) {
        ScenarioExecutor executor = executors.get(id);
        if (executor == null || !executor.supports(id)) {
            return ScenarioExecutor.Outcome.blocked("No verified isolated executor registered for " + id);
        }
        context.requireNotCancelled();
        ScenarioExecutor.Outcome result;
        try {
            result = executor.execute(context);
        } catch (Exception ex) {
            return new ScenarioExecutor.Outcome("FAILED", "Business executor failed: " + ex.getClass().getSimpleName(),
                Map.of("errorType", ex.getClass().getSimpleName(), "businessWorkflowExecuted", false));
        }
        if (result == null) {
            return ScenarioExecutor.Outcome.blocked("Executor did not provide a result");
        }
        if ("PASSED".equals(result.state())) {
            // A full pass must prove an actual positive and negative HTTP operation,
            // isolated database side effects, and browser/user journey evidence.
            Map<String, Object> proof = result.evidence();
            boolean complete = "FULL_BUSINESS_E2E".equals(executor.executionType()) &&
                Boolean.TRUE.equals(proof.get("businessWorkflowExecuted")) &&
                Boolean.TRUE.equals(proof.get("positiveHttpAssertion")) &&
                Boolean.TRUE.equals(proof.get("negativeHttpAssertion")) &&
                Boolean.TRUE.equals(proof.get("dbAssertion")) &&
                Boolean.TRUE.equals(proof.get("browserAssertion"));
            if (!complete) {
                return new ScenarioExecutor.Outcome("BLOCKED", "Missing full E2E proof; refusing PASSED",
                    Map.of("businessWorkflowExecuted", false, "kind", "INCOMPLETE_E2E_EVIDENCE"));
            }
        }
        return result;
    }
}
