package com.sakhtyar.assembly.application;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class Phase05AssemblyIndependentOracleTest {
    private static BigDecimal d(String s){return new BigDecimal(s);}
    @Test void syntheticWasteAndUnitPricingGolden(){
        var effective=AssemblyCostCalculator.effectiveQuantity(d("2.5"),d("0.10"),d("10"));
        assertEquals(d("27.500000"),effective);
        assertEquals(d("11000000.00"),AssemblyCostCalculator.lineTotal(effective,d("400000")));
    }
    @Test void rejectsNegativeQuantityOrPrice(){
        assertThrows(IllegalArgumentException.class,()->AssemblyCostCalculator.effectiveQuantity(d("-1"),d("0.1"),d("1")));
        assertThrows(IllegalArgumentException.class,()->AssemblyCostCalculator.lineTotal(d("1"),d("-5")));
    }
}