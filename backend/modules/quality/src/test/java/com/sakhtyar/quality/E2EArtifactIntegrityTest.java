package com.sakhtyar.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sakhtyar.identity.domain.UserRole;

class E2EArtifactIntegrityTest {
    @TempDir Path root;

    private static E2EExecutionContext context() {
        return new E2EExecutionContext(UUID.randomUUID(), UUID.randomUUID(), "owner",
            UUID.randomUUID(), "tester", UserRole.TESTER,
            Set.of("ROLE_TESTER", "QUALITY_VIEW", "QUALITY_RUN_SAFE"),
            "a".repeat(40), new AtomicBoolean(false), "trace-test");
    }

    private static Map<String, Object> evidence(Path root, E2EExecutionContext ctx) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("businessWorkflowExecuted", true);
        for (String key : Set.of("positiveHttpAssertion", "negativeHttpAssertion", "dbAssertion", "browserAssertion"))
            result.put(key, true);
        Map<String, Object> artifacts = new LinkedHashMap<>();
        for (String kind : Set.of("httpPositive", "httpNegative", "database", "browser")) {
            artifacts.put(kind, E2EArtifactIntegrity.writeRedactedText(root, ctx.workspaceId(), ctx.runId(),
                "E2E-01", kind, "Synthetic TEST only: " + kind + "; Bearer fake-only"));
        }
        result.put("proofArtifacts", artifacts);
        return result;
    }

    private static ScenarioExecutor executor(Map<String, Object> proof) {
        return new ScenarioExecutor() {
            @Override public String scenarioId() { return "E2E-01"; }
            @Override public String executionType() { return "FULL_BUSINESS_E2E"; }
            @Override public Outcome execute(E2EExecutionContext context) {
                return new Outcome("PASSED", "Synthetic contract proof only", proof);
            }
        };
    }

    @Test void acceptsVerifiedSyntheticProofWithRealBytes() throws Exception {
        var ctx = context();
        var proof = evidence(root, ctx);
        var registry = new ScenarioExecutorRegistry(java.util.List.of(executor(proof)), root.toString());
        assertThat(registry.execute("E2E-01", ctx).state()).isEqualTo("PASSED");
        var artifacts = (Map<?, ?>) proof.get("proofArtifacts");
        var first = (Map<?, ?>) artifacts.get("httpPositive");
        String bytes = Files.readString(root.resolve((String) first.get("path")));
        assertThat(bytes).doesNotContain("fake-only");
    }

    @Test void deniesForgedHashEvenWithAllBooleanAssertions() throws Exception {
        var ctx = context();
        var proof = evidence(root, ctx);
        var artifacts = (Map<String, Object>) proof.get("proofArtifacts");
        artifacts.put("browser", Map.of("path", ((Map<?, ?>) artifacts.get("browser")).get("path"), "sha256", "f".repeat(64)));
        assertThat(new ScenarioExecutorRegistry(java.util.List.of(executor(proof)), root.toString())
            .execute("E2E-01", ctx).state()).isEqualTo("BLOCKED");
    }

    @Test void deniesTraversalAndCrossWorkspaceEvidence() throws Exception {
        var ctx = context();
        var proof = evidence(root, ctx);
        var artifacts = (Map<String, Object>) proof.get("proofArtifacts");
        artifacts.put("httpPositive", Map.of("path", "evidence/../../sensitive.json", "sha256", "a".repeat(64)));
        assertThat(new ScenarioExecutorRegistry(java.util.List.of(executor(proof)), root.toString())
            .execute("E2E-01", ctx).state()).isEqualTo("BLOCKED");
        var other = context();
        assertThat(new ScenarioExecutorRegistry(java.util.List.of(executor(evidence(root, ctx))), root.toString())
            .execute("E2E-01", other).state()).isEqualTo("BLOCKED");
    }

    @Test void deniesModifiedBytesAfterHashCalculation() throws Exception {
        var ctx = context();
        var proof = evidence(root, ctx);
        var artifacts = (Map<?, ?>) proof.get("proofArtifacts");
        var record = (Map<?, ?>) artifacts.get("database");
        Files.writeString(root.resolve((String) record.get("path")), "tampered");
        assertThat(new ScenarioExecutorRegistry(java.util.List.of(executor(proof)), root.toString())
            .execute("E2E-01", ctx).state()).isEqualTo("BLOCKED");
    }

    @Test void refusesToWriteInvalidEvidenceKinds() {
        var ctx = context();
        assertThatThrownBy(() -> E2EArtifactIntegrity.writeRedactedText(root, ctx.workspaceId(), ctx.runId(),
            "E2E-01", "../../private", "fake"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
