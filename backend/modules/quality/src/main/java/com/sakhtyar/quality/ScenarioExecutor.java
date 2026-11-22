package com.sakhtyar.quality;

import java.util.Map;

/** Contract for one actual, isolated business scenario. Preflight is never a business pass. */
public interface ScenarioExecutor {
    String scenarioId();
    String executionType();

    default boolean supports(String scenario) { return scenarioId().equals(scenario); }

    Outcome execute(E2EExecutionContext context);

    record Outcome(String state, String summary, Map<String, Object> evidence) {
        public Outcome {
            if (!java.util.Set.of("PASSED", "FAILED", "BLOCKED").contains(state)) {
                throw new IllegalArgumentException("Unsupported scenario terminal state");
            }
            if (summary == null || summary.isBlank() || evidence == null) {
                throw new IllegalArgumentException("Outcome requires summary and evidence");
            }
            evidence = Map.copyOf(evidence);
        }

        public static Outcome blocked(String reason) {
            return new Outcome("BLOCKED", reason,
                Map.of("businessWorkflowExecuted", false, "kind", "UNIMPLEMENTED_BUSINESS_WORKFLOW"));
        }
    }
}
