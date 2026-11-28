package com.sakhtyar.assembly.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/** Definition validation; calculation policy stays in AssemblyCostCalculator. */
public final class AssemblyDefinitionPolicy {
    private AssemblyDefinitionPolicy() {}

    public static BigDecimal waste(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.TEN) > 0
                || value.stripTrailingZeros().scale() > 6)
            throw new IllegalArgumentException("Invalid waste factor.");
        return value.setScale(6, RoundingMode.UNNECESSARY);
    }

    public static String unchangedOutputUnit(String existing, String requested) {
        if (existing == null || requested == null || !existing.equalsIgnoreCase(requested.trim()))
            throw new IllegalArgumentException("Assembly output unit is immutable; create a new assembly.");
        return existing;
    }

    public static String optionalText(String value, int max) {
        if (value == null) return null;
        if (value.length() > max || value.codePoints().anyMatch(c -> Character.isISOControl(c)
                && c != '\n' && c != '\r' && c != '\t'))
            throw new IllegalArgumentException("Invalid assembly text.");
        return value.isBlank() ? null : value.strip();
    }

    public static Map<String,Object> metadata(Map<String,Object> value) {
        if (value == null) return Map.of();
        int[] budget = {65536, 2048};
        if (value.values().stream().anyMatch(v -> v == null))
            throw new IllegalArgumentException("Null top-level metadata value.");
        inspect(value, 0, budget);
        return Map.copyOf(value);
    }

    private static void inspect(Object node, int depth, int[] budget) {
        if (depth > 12 || --budget[1] < 0)
            throw new IllegalArgumentException("Assembly metadata too complex.");
        if (node instanceof Map<?,?> map) {
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || key.isBlank())
                    throw new IllegalArgumentException("Invalid metadata key.");
                budget[0] -= key.length();
                inspect(entry.getValue(), depth+1, budget);
            }
        } else if (node instanceof List<?> list) {
            for (Object item : list) inspect(item, depth+1, budget);
        } else if (node instanceof String s) {
            budget[0] -= s.length();
            if (s.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid metadata text.");
        } else if (node instanceof Number n) {
            if ((n instanceof Double d && !Double.isFinite(d)) || (n instanceof Float f && !Float.isFinite(f)))
                throw new IllegalArgumentException("Invalid metadata number.");
            if (n instanceof BigDecimal d && (d.precision() > 100 || Math.abs((long)d.scale()) > 100))
                throw new IllegalArgumentException("Metadata number too large.");
            budget[0] -= n.toString().length();
        } else if (node != null && !(node instanceof Boolean)) {
            throw new IllegalArgumentException("Metadata must be JSON-compatible.");
        }
        if (budget[0] < 0) throw new IllegalArgumentException("Assembly metadata too large.");
    }
}
