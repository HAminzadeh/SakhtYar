package com.sakhtyar.quality;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Versioned, conservative JSON evidence envelope: never persist raw credentials or contact fields. */
@Component
public final class E2EEvidenceCollector {
    private static final Pattern PRIVATE_KEY = Pattern.compile(
        "(?i).*(password|token|secret|authorization|cookie|api.?key|email|mobile|phone|credential|session|jwt|national.?id).*"
    );
    private static final Pattern EMAIL = Pattern.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=?");
    private static final Pattern IRAN_MOBILE = Pattern.compile("(?<![0-9])(?:\\+98|0)?9[0-9]{9}(?![0-9])");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");

    public Map<String, Object> collect(String runId, String scenarioId, String state,
                                       String executionType, String sourceSha,
                                       String traceId, Map<String, Object> data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("evidenceVersion", 1);
        envelope.put("runId", runId);
        envelope.put("scenarioId", scenarioId);
        envelope.put("state", state);
        envelope.put("executionType", executionType);
        envelope.put("sourceSha", sourceSha == null || sourceSha.isBlank() ? "unknown" : sourceSha);
        envelope.put("traceId", traceId == null ? "unknown" : traceId);
        envelope.put("capturedAt", Instant.now().toString());
        envelope.put("businessWorkflowExecuted", Boolean.TRUE.equals(data.get("businessWorkflowExecuted")));
        envelope.put("assertions", sanitize(data));
        return envelope;
    }

    public Object sanitize(Object input) {
        if (input == null) return null;
        if (input instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                out.put(key, PRIVATE_KEY.matcher(key).matches() ? "[REDACTED]" : sanitize(entry.getValue()));
            }
            return out;
        }
        if (input instanceof Iterable<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object item : list) out.add(sanitize(item));
            return out;
        }
        if (input instanceof CharSequence chars) {
            String safe = EMAIL.matcher(chars.toString()).replaceAll("[REDACTED_EMAIL]");
            safe = BEARER.matcher(safe).replaceAll("[REDACTED_BEARER]");
            safe = JWT.matcher(safe).replaceAll("[REDACTED_JWT]");
            return IRAN_MOBILE.matcher(safe).replaceAll("[REDACTED_PHONE]");
        }
        if (input instanceof Number || input instanceof Boolean) return input;
        return String.valueOf(input);
    }
}
