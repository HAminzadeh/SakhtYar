package com.sakhtyar.geo.application;

import java.math.BigDecimal;

/**
 * Shared WGS84 input rules for map requests. Coordinates are decimal degrees
 * (latitude, longitude); SQL uses (longitude, latitude) when constructing points.
 * Read-only searches accept greater precision than Property's seven-decimal storage.
 */
public final class GeoInputPolicy {
    private GeoInputPolicy() { }

    public record Point(BigDecimal latitude, BigDecimal longitude) { }

    public static Point point(BigDecimal latitude, BigDecimal longitude) {
        coordinate(latitude, new BigDecimal("-90"), new BigDecimal("90"), "lat");
        coordinate(longitude, new BigDecimal("-180"), new BigDecimal("180"), "lng");
        return new Point(latitude, longitude);
    }

    private static void coordinate(BigDecimal value, BigDecimal min, BigDecimal max, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required.");
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0)
            throw new IllegalArgumentException(field + " is outside WGS84 bounds.");
        // Avoid extremely long decimal URL inputs; keep precision adequate for geocoding.
        if (Math.max(0, value.stripTrailingZeros().scale()) > 14)
            throw new IllegalArgumentException(field + " has excessive decimal precision.");
    }

    public static String address(String value) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("Address is required.");
        String cleaned = value.trim();
        if (cleaned.length() > 300 || cleaned.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid address input.");
        return cleaned;
    }

    public static int radius(int value) {
        if (value < 1 || value > 20_000)
            throw new IllegalArgumentException("radiusMeters must be between 1 and 20000.");
        return value;
    }
}
