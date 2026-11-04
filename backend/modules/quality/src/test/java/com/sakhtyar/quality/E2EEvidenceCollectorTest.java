package com.sakhtyar.quality;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class E2EEvidenceCollectorTest {
    @Test void removesSensitiveEvidenceAndAddsVersion() {
        E2EEvidenceCollector c = new E2EEvidenceCollector();
        var evidence = c.collect("run", "E2E-01", "BLOCKED", "UNIMPLEMENTED", "sha", "trace",
            Map.of("authorization", "Bearer real-token", "nested", List.of(
                Map.of("email", "person@example.com", "safe", "Bearer token123")),
                "businessWorkflowExecuted", false));
        assertThat(evidence.get("evidenceVersion")).isEqualTo(1);
        assertThat(String.valueOf(evidence)).doesNotContain("real-token", "person@example.com", "token123");
        assertThat(evidence.get("executionType")).isEqualTo("UNIMPLEMENTED");
    }
}
