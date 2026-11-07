package com.sakhtyar.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sakhtyar.identity.domain.UserRole;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Synthetic, database-free safety tests. These never assert a real business E2E pass. */
class ScenarioExecutorFailClosedContractTest {
    private static E2EExecutionContext context() {
        return new E2EExecutionContext(UUID.randomUUID(), UUID.randomUUID(), "synthetic-owner",
            UUID.randomUUID(), "tester", UserRole.TESTER,
            Set.of("ROLE_TESTER", "QUALITY_VIEW", "QUALITY_RUN_SAFE"),
            "0".repeat(40), new AtomicBoolean(false), "synthetic-trace");
    }

    private static ScenarioExecutor executor(String id, String type, ScenarioExecutor.Outcome outcome) {
        return new ScenarioExecutor() {
            @Override public String scenarioId() { return id; }
            @Override public String executionType() { return type; }
            @Override public Outcome execute(E2EExecutionContext ctx) { return outcome; }
        };
    }

    @Test void rejectsNullOrMismatchedExecutorRegistration() {
        var blocked = ScenarioExecutor.Outcome.blocked("test only");
        assertThatThrownBy(() -> new ScenarioExecutorRegistry(null))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ScenarioExecutorRegistry(List.of(executor("not-e2e", "FULL_BUSINESS_E2E", blocked))))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ScenarioExecutorRegistry(List.of(executor("E2E-02", "", blocked))))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test void nullOutcomeAndMissingContextCannotPass() {
        var registry = new ScenarioExecutorRegistry(List.of(executor("E2E-02", "FULL_BUSINESS_E2E", null)));
        assertThat(registry.execute("E2E-02", context()).state()).isEqualTo("BLOCKED");
        assertThat(registry.execute("E2E-02", null).state()).isEqualTo("BLOCKED");
    }

    @Test void booleansWithoutRealArtifactsAreNotBusinessProof() {
        var assertionOnly = new ScenarioExecutor.Outcome("PASSED", "claims only", Map.of(
            "businessWorkflowExecuted", true, "positiveHttpAssertion", true,
            "negativeHttpAssertion", true, "dbAssertion", true, "browserAssertion", true));
        var registry = new ScenarioExecutorRegistry(List.of(executor("E2E-02", "FULL_BUSINESS_E2E", assertionOnly)));
        assertThat(registry.execute("E2E-02", context()).state()).isEqualTo("BLOCKED");
    }

    @Test void cancellationStopsExecutorBeforeExecution() {
        var ctx = context();
        ctx.cancellation().set(true);
        var registry = new ScenarioExecutorRegistry(List.of(
            executor("E2E-02", "FULL_BUSINESS_E2E", ScenarioExecutor.Outcome.blocked("sample"))));
        assertThatThrownBy(() -> registry.execute("E2E-02", ctx))
            .isInstanceOf(IllegalStateException.class);
    }
}
