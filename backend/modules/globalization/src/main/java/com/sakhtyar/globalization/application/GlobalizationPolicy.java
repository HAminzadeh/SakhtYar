package com.sakhtyar.globalization.application;

import java.math.BigDecimal;
import java.net.URI;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

/** Side-effect-free regional rules. Money is stored as decimals; display is not persistence. */
public final class GlobalizationPolicy {
    private GlobalizationPolicy() { }

    public static String search(String value) {
        if (value == null || value.isBlank()) return "%";
        if (value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid catalog search query.");
        String q = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .replace('\u064a', '\u06cc').replace('\u0643', '\u06a9')
                .toLowerCase(Locale.ROOT);
        if (q.length() > 100 || q.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid catalog search query.");
        // Literal search: user-supplied LIKE wildcards are not operators.
        return "%" + q.replace("\\", "\\\\").replace("%", "\\%")
                .replace("_", "\\_") + "%";
    }

    public static String timezone(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.length() > 100) throw new IllegalArgumentException("Invalid timezone.");
        try { return ZoneId.of(raw.trim()).getId(); }
        catch (Exception ex) { throw new IllegalArgumentException("Invalid timezone."); }
    }

    public static String dateFormat(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("ISO|GREGORIAN|JALALI"))
            throw new IllegalArgumentException("Unsupported date format.");
        return normalized;
    }

    public static String numberFormat(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("LATIN|PERSIAN|ARABIC"))
            throw new IllegalArgumentException("Unsupported number format.");
        return normalized;
    }

    public static Integer firstDay(Integer day) {
        if (day != null && (day < 1 || day > 7))
            throw new IllegalArgumentException("firstDayOfWeek must be 1..7.");
        return day;
    }

    public static BigDecimal positiveRate(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.precision() - value.scale() > 16 ||
                Math.max(0, value.stripTrailingZeros().scale()) > 12)
            throw new IllegalArgumentException("FX rate must be a positive NUMERIC(28,12) value.");
        return value;
    }

    public static BigDecimal amount(BigDecimal value) {
        if (value == null || value.precision() > 28 || Math.max(0, value.stripTrailingZeros().scale()) > 12)
            throw new IllegalArgumentException("Invalid monetary amount.");
        return value;
    }

    public static BigDecimal confidence(BigDecimal value) {
        if (value != null && (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0 ||
                Math.max(0, value.stripTrailingZeros().scale()) > 4))
            throw new IllegalArgumentException("Confidence must be within 0..1 (four decimals). ");
        return value;
    }

    public static String sourceLabel(String value) {
        if (value == null || value.trim().isBlank() || value.trim().length() > 300 ||
                value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Valid FX source label required.");
        return value.trim();
    }

    public static String sourceUrl(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > 2000) throw new IllegalArgumentException("Invalid FX source URL.");
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null ||
                    uri.getUserInfo() != null || uri.getFragment() != null)
                throw new IllegalArgumentException("FX source URL must use HTTPS.");
            return uri.toString();
        } catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Invalid FX source URL."); }
    }

    public static Instant observedAt(Instant time, Instant clock) {
        Instant result = time == null ? clock : time;
        if (result.isAfter(clock.plusSeconds(300)))
            throw new IllegalArgumentException("FX observation cannot be in the future.");
        return result;
    }

    public static BigDecimal tomanToRial(BigDecimal toman) {
        return amount(toman).multiply(BigDecimal.TEN);
    }
    public static BigDecimal rialToToman(BigDecimal rial) {
        return amount(rial).divide(BigDecimal.TEN);
    }
}
