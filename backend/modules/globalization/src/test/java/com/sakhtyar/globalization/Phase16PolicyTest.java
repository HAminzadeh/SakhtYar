package com.sakhtyar.globalization;

import com.sakhtyar.globalization.application.GlobalizationPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase16PolicyTest {
    @Test void normalizesPersianSearchAndEscapesWildcards() {
        assertEquals("%یک%",GlobalizationPolicy.search("يك"));
        assertEquals("%100\\%\\_ع%",GlobalizationPolicy.search("100%_ع"));
        assertEquals("%",GlobalizationPolicy.search("  "));
        assertThrows(IllegalArgumentException.class,()->GlobalizationPolicy.search("x".repeat(101)));
        assertThrows(IllegalArgumentException.class,()->GlobalizationPolicy.search("a\n"));
    }
    @Test void canonicalTimezoneAndDateFormats() {
        assertEquals("Asia/Tehran",GlobalizationPolicy.timezone("Asia/Tehran"));
        assertEquals("UTC",GlobalizationPolicy.timezone("UTC"));
        assertThrows(IllegalArgumentException.class,()->GlobalizationPolicy.timezone("Iran/invalid"));
        assertEquals("JALALI",GlobalizationPolicy.dateFormat("jalali"));
        assertEquals("GREGORIAN",GlobalizationPolicy.dateFormat("Gregorian"));
        assertThrows(IllegalArgumentException.class,()->GlobalizationPolicy.dateFormat("javascript:foo"));
        assertEquals("PERSIAN",GlobalizationPolicy.numberFormat("persian"));
        assertThrows(IllegalArgumentException.class,()->GlobalizationPolicy.numberFormat("wrong"));
    }
    @Test void validatesFxNumbersAndMetadata() {
        assertEquals(0,GlobalizationPolicy.positiveRate(new BigDecimal("10")).compareTo(BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,()->GlobalizationPolicy.positiveRate(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,()->GlobalizationPolicy.positiveRate(new BigDecimal("-0.1")));
        assertThrows(IllegalArgumentException.class,()->GlobalizationPolicy.positiveRate(new BigDecimal("0.1234567890123")));
        assertThrows(IllegalArgumentException.class,()->GlobalizationPolicy.confidence(new BigDecimal("1.1")));
        assertThrows(IllegalArgumentException.class,()->GlobalizationPolicy.confidence(new BigDecimal("0.12345")));
        assertEquals("Central bank",GlobalizationPolicy.sourceLabel(" Central bank "));
        assertThrows(IllegalArgumentException.class,()->GlobalizationPolicy.sourceUrl("http://example.com"));
        assertEquals("https://example.com/rate",GlobalizationPolicy.sourceUrl("https://example.com/rate"));
        assertThrows(IllegalArgumentException.class,()->GlobalizationPolicy.observedAt(Instant.parse("2035-01-01T00:00:00Z"),Instant.parse("2026-01-01T00:00:00Z")));
    }
    @Test void preservesExactTomanRialRelationAndWeekdayBounds(){
        assertEquals(0,new BigDecimal("1234.50").compareTo(GlobalizationPolicy.tomanToRial(new BigDecimal("123.45"))));
        assertEquals(0,new BigDecimal("123.45").compareTo(GlobalizationPolicy.rialToToman(new BigDecimal("1234.50"))));
        assertEquals(6,GlobalizationPolicy.firstDay(6));
        assertThrows(IllegalArgumentException.class,()->GlobalizationPolicy.firstDay(8));
    }
}
