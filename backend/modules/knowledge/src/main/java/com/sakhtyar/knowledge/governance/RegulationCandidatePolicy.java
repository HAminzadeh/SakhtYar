package com.sakhtyar.knowledge.governance;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, NON-AUTHORITATIVE suggestions from existing reviewed AI staging.
 * Never approves, publishes, binds or asserts the legal meaning of a text.
 */
public final class RegulationCandidatePolicy {
    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\p{N}])([0-9]+(?:\\.[0-9]+)?)(?![\\p{L}\\p{N}])");
    private RegulationCandidatePolicy() {}

    public record Suggestion(String normalizedText, String ruleType, String operator,
            String numericValue, String unit, int matchStart, int matchEnd,
            String reviewState, String parserConfidence, List<String> warnings) {}

    public static String normalize(String raw) {
        if (raw == null) return "";
        StringBuilder text = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '\u06f0' && c <= '\u06f9') c = (char) ('0' + c - '\u06f0');
            else if (c >= '\u0660' && c <= '\u0669') c = (char) ('0' + c - '\u0660');
            else if (c == '\u066b') c = '.';
            else if (c == '\u066c') c = ',';
            else if (c == '\u064a' || c == '\u0649') c = '\u06cc';
            else if (c == '\u0643') c = '\u06a9';
            text.append(c);
        }
        return text.toString().replaceAll("[\\t\\r\\n ]+", " ").strip();
    }

    public static Suggestion propose(String original) {
        String text = normalize(original);
        List<String> warnings = new ArrayList<>();
        if (text.isEmpty()) {
            return new Suggestion(text, null, null, null, null, -1, -1,
                    "REVIEW_REQUIRED", "UNRESOLVED", List.of("EMPTY_TEXT"));
        }
        if (text.contains("،") || text.contains(",")) {
            warnings.add("PUNCTUATION_OR_THOUSANDS_SEPARATOR_REVIEW");
        }
        Matcher m = NUMBER.matcher(text);
        int count = 0, start = -1, end = -1;
        String numeric = null;
        while (m.find()) {
            count++;
            if (count == 1) {
                start = m.start(1);
                end = m.end(1);
                numeric = m.group(1);
            }
            if (count > 1) break;
        }
        if (count == 0) warnings.add("NO_UNAMBIGUOUS_NUMBER");
        if (count > 1) warnings.add("MULTIPLE_NUMBERS");
        String rule = null, operator = null, unit = null;
        String lower = text.toLowerCase(Locale.ROOT);
        if (count == 1 && !warnings.contains("PUNCTUATION_OR_THOUSANDS_SEPARATOR_REVIEW")) {
            boolean max = lower.contains("حداکثر");
            boolean min = lower.contains("حداقل");
            if (max && !min) {
                operator = "<=";
                if (lower.contains("سطح اشغال") && lower.contains("درصد")) {rule = "MAX_COVERAGE_PERCENT"; unit = "PERCENT";}
                else if (lower.contains("ارتفاع") && lower.contains("متر")) {rule = "MAX_HEIGHT"; unit = "M";}
                else if (lower.contains("طبقه")) {rule = "MAX_FLOORS"; unit = "FLOOR";}
            } else if (min && !max) {
                operator = ">=";
                if (lower.contains("عرض گذر") && lower.contains("متر")) {rule = "MIN_PASSAGE_WIDTH"; unit = "M";}
                else if (lower.contains("بر ملک") && lower.contains("متر")) {rule = "MIN_FRONTAGE"; unit = "M";}
            }
        }
        if (rule == null) {
            warnings.add("UNSUPPORTED_OR_AMBIGUOUS_SEMANTICS");
            return new Suggestion(text, null, null, null, null, start, end,
                    "REVIEW_REQUIRED", "UNRESOLVED", List.copyOf(warnings));
        }
        try {
            numeric = new BigDecimal(numeric).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ex) {
            warnings.add("NUMERIC_PARSE_FAILED");
            return new Suggestion(text, null, null, null, null, start, end,
                    "REVIEW_REQUIRED", "UNRESOLVED", List.copyOf(warnings));
        }
        warnings.add("HUMAN_LEGAL_REVIEW_REQUIRED");
        return new Suggestion(text, rule, operator, numeric, unit, start, end,
                "PROPOSED_ONLY", "LOW_HEURISTIC_ONLY", List.copyOf(warnings));
    }

    /** Stable identity; different source, content or reviewed semantic scope => different fingerprint. */
    public static String fingerprint(String sourceId, String contentHash, String semanticKey) {
        if (sourceId == null || sourceId.isBlank() || contentHash == null || contentHash.isBlank())
            throw new IllegalArgumentException("Provenance ID and content hash are mandatory");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = sourceId + "\u001f" + contentHash.toLowerCase(Locale.ROOT)
                    + "\u001f" + normalize(semanticKey == null ? "" : semanticKey);
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
