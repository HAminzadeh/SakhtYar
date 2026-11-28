package com.sakhtyar.assembly.application;

import com.sakhtyar.assembly.domain.PriceBasis;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase19BCalculationTest {
    static BigDecimal d(String s){return new BigDecimal(s);}
    @Test void independentTwoLineGoldenAndUnitCost(){
        // Independent hand-computed oracle: 12 * 1.05 * 100 * 4000 = 5,040,000;
        // 8 * 1.03 * 100 * 5000 = 4,120,000.
        var q1=AssemblyCostCalculator.effectiveQuantity(d("12"),d("0.05"),d("100"));
        var q2=AssemblyCostCalculator.effectiveQuantity(d("8"),d("0.03"),d("100"));
        assertEquals(d("1260.000000"),q1);assertEquals(d("824.000000"),q2);
        var t=AssemblyCalculationPolicy.addLine(d("5040000.00"),AssemblyCostCalculator.lineTotal(q2,d("5000")));
        assertEquals(d("5040000.00"),AssemblyCostCalculator.lineTotal(q1,d("4000")));
        assertEquals(d("9160000.00"),t);assertEquals(d("91600.00"),AssemblyCalculationPolicy.unitCost(t,d("100")));
    }
    @Test void usesAllPriceBases(){
        for(var basis:PriceBasis.values()) {
            String expected=switch(basis){case MIN->"10";case MAX->"40";case AVERAGE->"22";case MEDIAN->"20";};
            assertEquals(d(expected),AssemblyCostCalculator.choosePrice(basis,d("10"),d("40"),d("22"),d("20")));
        }
    }
    @Test void roundsHalfUpAtBothBoundaries(){
        assertEquals(d("0.000002"),AssemblyCostCalculator.effectiveQuantity(d("0.000001"),d("0.5"),d("1")));
        assertEquals(d("0.01"),AssemblyCostCalculator.lineTotal(d("0.005"),d("1")));
        assertEquals(d("0.34"),AssemblyCalculationPolicy.unitCost(d("1.01"),d("3")));
        assertEquals(d("999999999999999999.99"),AssemblyCalculationPolicy.price(d("999999999999999999.99")));
    }
    @Test void rejectsHostileInputsAndOverflows(){
        assertThrows(IllegalArgumentException.class,()->AssemblyCalculationPolicy.quantity(null));
        assertThrows(IllegalArgumentException.class,()->AssemblyCalculationPolicy.quantity(d("0")));
        assertThrows(IllegalArgumentException.class,()->AssemblyCalculationPolicy.quantity(d("-1")));
        assertThrows(IllegalArgumentException.class,()->AssemblyCalculationPolicy.quantity(d("0.0000001")));
        assertThrows(IllegalArgumentException.class,()->AssemblyCalculationPolicy.quantity(d("1e100000")));
        assertThrows(IllegalArgumentException.class,()->AssemblyCalculationPolicy.quantity(d("100000000000000")));
        assertThrows(IllegalArgumentException.class,()->AssemblyCalculationPolicy.waste(d("10.000001")));
        assertThrows(IllegalArgumentException.class,()->AssemblyCalculationPolicy.waste(d("-0.01")));
        assertThrows(IllegalArgumentException.class,()->AssemblyCalculationPolicy.waste(d("0.0000001")));
        assertThrows(IllegalArgumentException.class,()->AssemblyCostCalculator.effectiveQuantity(d("0.000001"),d("0"),d("0.000001")));
        assertThrows(IllegalArgumentException.class,()->AssemblyCostCalculator.effectiveQuantity(d("99999999999999"),d("0"),d("2")));
        assertThrows(IllegalArgumentException.class,()->AssemblyCostCalculator.lineTotal(d("99999999999999"),d("999999999999999999")));
        assertThrows(IllegalArgumentException.class,()->AssemblyCalculationPolicy.addLine(d("999999999999999999.99"),d("0.01")));
        assertThrows(IllegalArgumentException.class,()->AssemblyCalculationPolicy.unitCost(d("0.01"),d("100")));
        assertThrows(IllegalArgumentException.class,()->AssemblyCalculationPolicy.validateStatistics(d("1"),d("5"),d("6"),d("2")));
        assertThrows(IllegalArgumentException.class,()->AssemblyCalculationPolicy.validateStatistics(d("0"),d("5"),d("3"),d("2")));
    }
}
