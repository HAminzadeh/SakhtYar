package com.sakhtyar.quality;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class E2EEvidenceCollectorHardeningTest {
    @Test void redactsSecretsInMapKeysAndUnstructuredText() {
        var collector = new E2EEvidenceCollector();
        var out = collector.collect("run", "E2E-01", "BLOCKED", "UNIMPLEMENTED", "bad-hash", "trace",
            Map.of("cookie", "session-123", "message", "password=hunter2 person@example.com Bearer fake123",
                   "businessWorkflowExecuted", false));
        assertThat(String.valueOf(out)).doesNotContain("hunter2", "session-123", "example.com", "fake123");
        assertThat(out.get("sourceSha")).isEqualTo("UNVERIFIED");
    }

    @Test void acceptsNullInputAndStopsCyclesWithoutRecursionError() {
        var collector = new E2EEvidenceCollector();
        assertThat(collector.collect("run", "E2E-01", "BLOCKED", "LIMITED", null, null, null))
            .containsEntry("businessWorkflowExecuted", false);
        Map<String, Object> loop = new LinkedHashMap<>();
        loop.put("child", loop);
        assertThat(String.valueOf(collector.sanitize(loop))).contains("[CIRCULAR]");
    }
}
