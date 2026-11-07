package com.sakhtyar.assembly.application;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase05AssemblyGoldenTest {
    private static BigDecimal d(String value) { return new BigDecimal(value); }
    @Test void independentGoldenWasteQuantityAndCost() {
        BigDecimal q=AssemblyCostCalculator.effectiveQuantity(d("2.5"),d("0.10"),d("10"));
        assertEquals(d("27.500000"),q);
        assertEquals(d("11000000.00"),AssemblyCostCalculator.lineTotal(q,d("400000")));
    }
    @Test void rejectsNegativeQuantitiesAndPrices() {
        assertThrows(IllegalArgumentException.class,
            ()->AssemblyCostCalculator.effectiveQuantity(d("2.5"),d("-0.01"),d("10")));
        assertThrows(IllegalArgumentException.class,
            ()->AssemblyCostCalculator.lineTotal(d("1"),d("-1")));
    }
}
