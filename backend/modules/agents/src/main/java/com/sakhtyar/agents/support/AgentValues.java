package com.sakhtyar.agents.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AgentValues {

    private AgentValues() {
    }

    public static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> {
            if (key != null) {
                result.put(String.valueOf(key), item);
            }
        });
        return result;
    }

    public static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        ArrayList<Map<String, Object>> result = new ArrayList<>();
        for (Object item : raw) {
            Map<String, Object> mapped = map(item);
            if (!mapped.isEmpty()) {
                result.add(mapped);
            }
        }
        return result;
    }

    public static List<String> stringList(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        return raw.stream()
                .filter(item -> item != null && !String.valueOf(item).isBlank())
                .map(String::valueOf)
                .toList();
    }

    public static BigDecimal decimal(Map<String, ?> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            BigDecimal value = decimal(source.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public static BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            String text = String.valueOf(value)
                    .replace(",", "")
                    .trim();
            return text.isBlank() ? null : new BigDecimal(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static Integer integer(Map<String, ?> source, String... keys) {
        BigDecimal value = decimal(source, keys);
        return value == null ? null : value.intValue();
    }

    public static String text(Map<String, ?> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    public static boolean bool(Map<String, ?> source, String key, boolean defaultValue) {
        if (source == null || source.get(key) == null) {
            return defaultValue;
        }
        Object value = source.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
