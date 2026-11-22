package com.sakhtyar.audit.application;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * Audit payloads are intentionally a tiny allowlist, NOT a generic serializer of
 * requests, exceptions, domain entities or HTTP headers. Unknown keys and values
 * never enter the database. Raw actor identity is restricted to AUDIT_READ readers.
 */
public final class AuditPayloadSanitizer {
    private static final Set<String> LABELS = Set.of(
            "role", "status", "state", "reasonCode", "result", "decision",
            "phase", "scope", "mode", "source");
    private static final Set<String> COUNTS = Set.of("attempts", "count", "revision", "version");
    private static final Set<String> FLAGS = Set.of("success", "readOnly");
    private static final Pattern LABEL = Pattern.compile("[A-Za-z0-9_.:-]{1,80}");
    private static final Pattern TRACE = Pattern.compile("(?:[0-9a-fA-F]{16}|[0-9a-fA-F]{32})");
    private static final int MAX_FIELDS = 32;

    private AuditPayloadSanitizer() { }

    public static Map<String, Object> sanitize(Map<String, ?> source) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("auditSchemaVersion", 1);
        String trace = MDC.get("traceId");
        if (trace != null && TRACE.matcher(trace).matches()) {
            output.put("traceId", trace.toLowerCase(Locale.ROOT));
        }
        int redacted = 0;
        if (source != null) {
            for (Map.Entry<String, ?> entry : source.entrySet()) {
                if (++redacted > MAX_FIELDS) {
                    throw new IllegalArgumentException("Audit payload field limit exceeded");
                }
                String key = entry.getKey();
                Object value = entry.getValue();
                if (key == null || key.isBlank()) {
                    continue;
                }
                if (LABELS.contains(key) && value instanceof String label
                        && LABEL.matcher(label).matches()) {
                    output.put(key, label);
                } else if (COUNTS.contains(key) && value instanceof Integer count && count >= 0) {
                    output.put(key, count);
                } else if (COUNTS.contains(key) && value instanceof Long count && count >= 0) {
                    output.put(key, count);
                } else if (FLAGS.contains(key) && value instanceof Boolean flag) {
                    output.put(key, flag);
                } // All free text, PII, secrets, nested maps and other keys are discarded.
            }
        }
        // Never accept traceId, workspaceId or scope identity from caller-supplied payloads.
        // Workspace proof requires a trusted server-side workspace context and DB RLS checks.
        return Map.copyOf(output);
    }

    /** Prevent raw user-supplied strings in unstructured top-level action fields. */
    public static String safeCode(String label, String value, int maxLength) {
        if (value == null || value.length() > maxLength
                || !value.matches("[A-Za-z0-9_.:-]{1," + maxLength + "}")) {
            throw new IllegalArgumentException("Invalid audit " + label);
        }
        return value;
    }
}
