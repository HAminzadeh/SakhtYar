package com.sakhtyar.quality;

import java.time.Instant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Versioned, bounded, conservative evidence envelope. Callers must exclude raw PII and credentials. */
@Component
public final class E2EEvidenceCollector {
    private static final Pattern PRIVATE_KEY = Pattern.compile(
        "(?i).*(password|token|secret|authorization|cookie|api.?key|email|mobile|phone|credential|session|jwt|national.?id).*"
    );
    private static final Pattern EMAIL = Pattern.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=?");
    private static final Pattern IRAN_MOBILE = Pattern.compile("(?<![0-9])(?:\\+98|0)?9[0-9]{9}(?![0-9])");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    private static final Pattern INLINE_SECRET = Pattern.compile(
        "(?i)\\b(password|token|secret|api[-_]?key|authorization|cookie|jwt)\\s*[:=]\\s*[^\\s,;}\\\"']+"
    );

    public Map<String, Object> collect(String runId, String scenarioId, String state,
                                       String executionType, String sourceSha,
                                       String traceId, Map<String, Object> data) {
        Map<String, Object> safeData = data == null ? Map.of() : data;
        Object sanitized = sanitize(safeData);
        Map<?, ?> safe = sanitized instanceof Map<?, ?> map ? map : Map.of();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("evidenceVersion", 1);
        envelope.put("runId", runId);
        envelope.put("scenarioId", scenarioId);
        envelope.put("state", state);
        envelope.put("executionType", executionType);
        envelope.put("sourceSha", sourceSha != null && sourceSha.matches("(?i)[0-9a-f]{40}") ? sourceSha : "UNVERIFIED");
        envelope.put("traceId", traceId == null ? "unknown" : sanitize(traceId));
        envelope.put("capturedAt", Instant.now().toString());
        envelope.put("businessWorkflowExecuted", Boolean.TRUE.equals(safeData.get("businessWorkflowExecuted")));
        if (safe.containsKey("proofArtifacts")) envelope.put("proofArtifacts", safe.get("proofArtifacts"));
        envelope.put("assertions", sanitized);
        return envelope;
    }

    public Object sanitize(Object input) {
        return sanitize(input, new IdentityHashMap<>(), 0);
    }

    private Object sanitize(Object input, IdentityHashMap<Object, Boolean> visiting, int depth) {
        if (input == null) return null;
        if (depth > 12) return "[TRUNCATED_DEPTH]";
        if (input instanceof Map<?, ?> map) {
            if (visiting.put(map, Boolean.TRUE) != null) return "[CIRCULAR]";
            try {
                Map<String, Object> out = new LinkedHashMap<>();
                int count = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (++count > 150) { out.put("_truncated", true); break; }
                    String key = String.valueOf(entry.getKey());
                    out.put(key, PRIVATE_KEY.matcher(key).matches() ? "[REDACTED]" :
                            sanitize(entry.getValue(), visiting, depth + 1));
                }
                return out;
            } finally { visiting.remove(map); }
        }
        if (input instanceof Iterable<?> list) {
            if (visiting.put(list, Boolean.TRUE) != null) return "[CIRCULAR]";
            try {
                List<Object> out = new ArrayList<>();
                int count = 0;
                for (Object item : list) {
                    if (++count > 150) { out.add("[TRUNCATED_LIST]"); break; }
                    out.add(sanitize(item, visiting, depth + 1));
                }
                return out;
            } finally { visiting.remove(list); }
        }
        if (input instanceof CharSequence chars) {
            String safe = chars.toString();
            if (safe.length() > 4096) safe = safe.substring(0, 4096) + "[TRUNCATED]";
            safe = EMAIL.matcher(safe).replaceAll("[REDACTED_EMAIL]");
            safe = BEARER.matcher(safe).replaceAll("[REDACTED_BEARER]");
            safe = JWT.matcher(safe).replaceAll("[REDACTED_JWT]");
            safe = IRAN_MOBILE.matcher(safe).replaceAll("[REDACTED_PHONE]");
            return INLINE_SECRET.matcher(safe).replaceAll("[REDACTED_SECRET]");
        }
        if (input instanceof Number || input instanceof Boolean) return input;
        return sanitize(String.valueOf(input), visiting, depth + 1);
    }
}
