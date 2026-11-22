package com.sakhtyar.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import com.sakhtyar.identity.domain.UserRole;

class ScenarioExecutorRegistryTest {
    private static E2EExecutionContext context() {
        return new E2EExecutionContext(UUID.randomUUID(), UUID.randomUUID(), "owner",
            UUID.randomUUID(), "tester", UserRole.TESTER,
            Set.of("ROLE_TESTER", "QUALITY_VIEW", "QUALITY_RUN_SAFE"),
            "test-commit", new AtomicBoolean(false), UUID.randomUUID().toString());
    }

    private static ScenarioExecutor executor(String id, String kind, ScenarioExecutor.Outcome outcome) {
        return new ScenarioExecutor() {
            public String scenarioId() { return id; }
            public String executionType() { return kind; }
            public Outcome execute(E2EExecutionContext ctx) { return outcome; }
        };
    }

    @Test void unregisteredScenarioNeverPasses() {
        var registry = new ScenarioExecutorRegistry(List.of());
        assertThat(registry.supports("E2E-01")).isFalse();
        assertThat(registry.execute("E2E-01", context()).state()).isEqualTo("BLOCKED");
    }

    @Test void rejectsDuplicateRegistration() {
        var out = ScenarioExecutor.Outcome.blocked("not implemented");
        assertThatThrownBy(() -> new ScenarioExecutorRegistry(List.of(
            executor("E2E-01", "LIMITED", out), executor("E2E-01", "LIMITED", out))))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test void rejectsFakePassFromLimitedFixture() {
        var out = new ScenarioExecutor.Outcome("PASSED", "No real E2E", Map.of("businessWorkflowExecuted", true));
        var registry = new ScenarioExecutorRegistry(List.of(executor("E2E-14", "LIMITED_CORE_FIXTURE", out)));
        assertThat(registry.execute("E2E-14", context()).state()).isEqualTo("BLOCKED");
    }

    @Test void refusesUnsupportedAuthoritiesInContext() {
        assertThatThrownBy(() -> new E2EExecutionContext(UUID.randomUUID(), UUID.randomUUID(), "owner",
            UUID.randomUUID(), "tester", UserRole.TESTER, Set.of("ROLE_ADMIN", "USER_MANAGE"),
            "sha", new AtomicBoolean(false), "trace"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
