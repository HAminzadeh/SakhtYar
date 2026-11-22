package com.sakhtyar.quality;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** A unique executor per scenario. Missing/unsupported implementations fail closed. */
@Component
public final class ScenarioExecutorRegistry {
    private final Map<String, ScenarioExecutor> executors;
    private final String evidenceRoot;

    /** Convenience constructor for isolated contract tests. Does not allow a business PASS. */
    public ScenarioExecutorRegistry(List<ScenarioExecutor> provided) { this(provided, ""); }

    @Autowired
    public ScenarioExecutorRegistry(List<ScenarioExecutor> provided,
                                    @Value("${sakhtyar.e2e.evidence-root:}") String evidenceRoot) {
        if (provided == null) throw new IllegalStateException("Executor registry must be initialized");
        Map<String, ScenarioExecutor> found = new LinkedHashMap<>();
        for (ScenarioExecutor executor : provided) {
            if (executor == null || executor.scenarioId() == null ||
                !executor.scenarioId().matches("E2E-(0[1-9]|1[0-9]|2[0-4])") ||
                executor.executionType() == null || executor.executionType().isBlank() ||
                !executor.supports(executor.scenarioId())) {
                throw new IllegalStateException("Invalid scenario executor registration");
            }
            if (found.putIfAbsent(executor.scenarioId(), executor) != null) {
                throw new IllegalStateException("Duplicate executor for " + executor.scenarioId());
            }
        }
        executors = Map.copyOf(found);
        this.evidenceRoot = evidenceRoot == null ? "" : evidenceRoot;
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
        if (context == null) return ScenarioExecutor.Outcome.blocked("Missing restricted execution context");
        context.requireNotCancelled();
        ScenarioExecutor.Outcome result;
        try {
            result = executor.execute(context);
        } catch (Exception ex) {
            return new ScenarioExecutor.Outcome("FAILED", "Business executor failed: " + ex.getClass().getSimpleName(),
                Map.of("errorType", ex.getClass().getSimpleName(), "businessWorkflowExecuted", false));
        }
        if (result == null) return ScenarioExecutor.Outcome.blocked("Executor did not provide a result");
        if ("PASSED".equals(result.state())) {
            Map<String, Object> proof = result.evidence();
            boolean assertions = "FULL_BUSINESS_E2E".equals(executor.executionType()) &&
                Boolean.TRUE.equals(proof.get("businessWorkflowExecuted")) &&
                Boolean.TRUE.equals(proof.get("positiveHttpAssertion")) &&
                Boolean.TRUE.equals(proof.get("negativeHttpAssertion")) &&
                Boolean.TRUE.equals(proof.get("dbAssertion")) &&
                Boolean.TRUE.equals(proof.get("browserAssertion"));
            boolean artifacts = assertions && E2EArtifactIntegrity.verified(evidenceRoot,
                context.workspaceId(), context.runId(), id, proof);
            if (!artifacts) {
                return new ScenarioExecutor.Outcome("BLOCKED", "Missing verified on-disk E2E proof; refusing PASSED",
                    Map.of("businessWorkflowExecuted", false, "kind", "INCOMPLETE_E2E_EVIDENCE"));
            }
        }
        return result;
    }
}
