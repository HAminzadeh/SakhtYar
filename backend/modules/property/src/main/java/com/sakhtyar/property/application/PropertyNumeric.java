package com.sakhtyar.property.application;

import java.math.BigDecimal;

/**
 * Property measurements are expressed in SI units: m, m2 and decimal WGS84 degrees.
 * The numeric limits are the existing PostgreSQL NUMERIC column contracts.
 * This does not replace spatial polygon validation or cadastral verification.
 */
public final class PropertyNumeric {
    private PropertyNumeric() { }

    public static BigDecimal localizedDecimal(Object raw) {
        if (raw == null) throw new IllegalArgumentException("Numeric value is required.");
        if (raw instanceof BigDecimal decimal) return decimal;
        if (raw instanceof Number number) {
            try { return new BigDecimal(number.toString()); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid numeric value.", ex); }
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) throw new IllegalArgumentException("Numeric value is required.");
        StringBuilder ascii = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= '\u06f0' && ch <= '\u06f9') ascii.append((char) ('0' + ch - '\u06f0'));
            else if (ch >= '\u0660' && ch <= '\u0669') ascii.append((char) ('0' + ch - '\u0660'));
            else if (ch == '\u066b') ascii.append('.');
            else if (ch == '\u066c') ascii.append(ch);
            else ascii.append(ch);
        }
        String value = ascii.toString();
        // A grouping character is permitted only between proper groups of three digits.
        if (value.indexOf('\u066c') >= 0) {
            if (!value.matches("[+-]?[0-9]{1,3}(?:\u066c[0-9]{3})+(?:\\.[0-9]+)?")) {
                throw new IllegalArgumentException("Invalid thousands grouping.");
            }
            value = value.replace("\u066c", "");
        }
        // Refuse arbitrary ASCII commas and scientific notation in localized strings.
        if (!value.matches("[+-]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)")) {
            throw new IllegalArgumentException("Invalid numeric value.");
        }
        return new BigDecimal(value);
    }

    public static int localizedInteger(Object raw) {
        try {
            return localizedDecimal(raw).intValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Integer value required.", ex);
        }
    }

    /** Coordinate pair, bounds, scale and existing NUMERIC column precision. */
    public static void validate(
            BigDecimal landAreaM2, BigDecimal frontageM,
            BigDecimal passageWidthM, BigDecimal buildingAreaM2,
            BigDecimal latitude, BigDecimal longitude) {
        positive(landAreaM2, 10, 2, "landAreaM2");
        positive(frontageM, 8, 2, "frontageM");
        positive(passageWidthM, 8, 2, "passageWidthM");
        positive(buildingAreaM2, 10, 2, "buildingAreaM2");
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("Latitude and longitude must be supplied together.");
        }
        coordinate(latitude, new BigDecimal("-90"), new BigDecimal("90"), "latitude");
        coordinate(longitude, new BigDecimal("-180"), new BigDecimal("180"), "longitude");
    }

    private static void positive(BigDecimal value, int integerDigits, int fractionalDigits, String field) {
        if (value == null) return;
        if (value.signum() <= 0) throw new IllegalArgumentException(field + " must be positive.");
        precision(value, integerDigits, fractionalDigits, field);
    }

    private static void coordinate(BigDecimal value, BigDecimal min, BigDecimal max, String field) {
        if (value == null) return;
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException(field + " is outside WGS84 bounds.");
        }
        precision(value, 3, 7, field);
    }

    private static void precision(BigDecimal value, int integral, int scale, String field) {
        BigDecimal normalized = value.stripTrailingZeros();
        int fraction = Math.max(0, normalized.scale());
        int integralCount = Math.max(0, normalized.precision() - normalized.scale());
        if (fraction > scale || integralCount > integral) {
            throw new IllegalArgumentException(field + " exceeds database numeric precision.");
        }
    }
}
