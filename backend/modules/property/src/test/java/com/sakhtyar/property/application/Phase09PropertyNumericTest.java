package com.sakhtyar.property.application;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class Phase09PropertyNumericTest {
    @Test void parsesPersianArabicAndAsciiDecimalsExactly() {
        assertEquals(new BigDecimal("1234.50"), PropertyNumeric.localizedDecimal("۱٬۲۳۴٫۵۰"));
        assertEquals(new BigDecimal("12.75"), PropertyNumeric.localizedDecimal("١٢٫٧٥"));
        assertEquals(new BigDecimal("25.1"), PropertyNumeric.localizedDecimal("25.1"));
        assertEquals(1405, PropertyNumeric.localizedInteger("۱۴۰۵"));
    }
    @Test void refusesMalformedNumbersAndFractionalInteger() {
        assertThrows(IllegalArgumentException.class, () -> PropertyNumeric.localizedDecimal("1e12"));
        assertThrows(IllegalArgumentException.class, () -> PropertyNumeric.localizedDecimal("1,234"));
        assertThrows(IllegalArgumentException.class, () -> PropertyNumeric.localizedDecimal("۱۲٬۳۴"));
        assertThrows(IllegalArgumentException.class, () -> PropertyNumeric.localizedDecimal("null"));
        assertThrows(IllegalArgumentException.class, () -> PropertyNumeric.localizedInteger("۱۲٫۵"));
    }
    @Test void requiresCompleteWgs84CoordinatePair() {
        assertThrows(IllegalArgumentException.class, () -> PropertyNumeric.validate(null,null,null,null,new BigDecimal("35"),null));
        assertThrows(IllegalArgumentException.class, () -> PropertyNumeric.validate(null,null,null,null,null,new BigDecimal("51")));
        assertDoesNotThrow(() -> PropertyNumeric.validate(null,null,null,null,null,null));
    }
    @Test void rejectsOutOfBoundsCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> PropertyNumeric.validate(null,null,null,null,new BigDecimal("91"),new BigDecimal("51")));
        assertThrows(IllegalArgumentException.class, () -> PropertyNumeric.validate(null,null,null,null,new BigDecimal("35"),new BigDecimal("181")));
    }
    @Test void rejectsSilentPostgresNumericRounding() {
        assertThrows(IllegalArgumentException.class, () -> PropertyNumeric.validate(new BigDecimal("12.345"),null,null,null,null,null));
        assertThrows(IllegalArgumentException.class, () -> PropertyNumeric.validate(null,new BigDecimal("9.999"),null,null,null,null));
        assertThrows(IllegalArgumentException.class, () -> PropertyNumeric.validate(null,null,null,null,new BigDecimal("35.12345678"),new BigDecimal("51")));
    }
    @Test void acceptsExactPostgresqlPrecisionAndSiUnits() {
        assertDoesNotThrow(() -> PropertyNumeric.validate(new BigDecimal("1234.50"),new BigDecimal("12.50"),new BigDecimal("8.00"),new BigDecimal("1800.00"),new BigDecimal("35.7234567"),new BigDecimal("51.4067890")));
    }
    @Test void refusesNegativeMeasurementsAndOverflow() {
        assertThrows(IllegalArgumentException.class, () -> PropertyNumeric.validate(BigDecimal.ZERO,null,null,null,null,null));
        assertThrows(IllegalArgumentException.class, () -> PropertyNumeric.validate(null,new BigDecimal("100000000"),null,null,null,null));
    }
}
