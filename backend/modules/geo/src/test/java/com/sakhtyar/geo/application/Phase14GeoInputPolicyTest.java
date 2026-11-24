package com.sakhtyar.geo.application;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase14GeoInputPolicyTest {
    private static BigDecimal n(String x) { return new BigDecimal(x); }

    @Test void acceptsWgs84EdgesAndPreservesOrder() {
        assertEquals(n("-90"), GeoInputPolicy.point(n("-90"), n("180")).latitude());
        assertEquals(n("-180"), GeoInputPolicy.point(n("90"), n("-180")).longitude());
        assertEquals(n("35.6892"), GeoInputPolicy.point(n("35.6892"), n("51.3890")).latitude());
    }

    @Test void rejectsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> GeoInputPolicy.point(n("90.0001"), n("0")));
        assertThrows(IllegalArgumentException.class, () -> GeoInputPolicy.point(n("-90.0001"), n("0")));
        assertThrows(IllegalArgumentException.class, () -> GeoInputPolicy.point(n("0"), n("180.0001")));
        assertThrows(IllegalArgumentException.class, () -> GeoInputPolicy.point(n("0"), n("-180.0001")));
    }

    @Test void refusesNullAndOversizedFractionalPrecision() {
        assertThrows(IllegalArgumentException.class, () -> GeoInputPolicy.point(null, n("0")));
        assertThrows(IllegalArgumentException.class, () -> GeoInputPolicy.point(n("0"), null));
        assertThrows(IllegalArgumentException.class, () -> GeoInputPolicy.point(n("0.123456789012345"), n("0")));
        assertEquals(n("0.12345678901234"), GeoInputPolicy.point(n("0.12345678901234"), n("0")).latitude());
    }

    @Test void addressIsTrimmedAndControlCharactersRejected() {
        assertEquals("تهران، میدان آزادی", GeoInputPolicy.address("  تهران، میدان آزادی  "));
        assertThrows(IllegalArgumentException.class, () -> GeoInputPolicy.address("  "));
        assertThrows(IllegalArgumentException.class, () -> GeoInputPolicy.address("Tehran\nprivate"));
        assertThrows(IllegalArgumentException.class, () -> GeoInputPolicy.address("X".repeat(301)));
    }

    @Test void radiusBoundary() {
        assertEquals(1, GeoInputPolicy.radius(1));
        assertEquals(20_000, GeoInputPolicy.radius(20_000));
        assertThrows(IllegalArgumentException.class, () -> GeoInputPolicy.radius(0));
        assertThrows(IllegalArgumentException.class, () -> GeoInputPolicy.radius(20_001));
    }
}
