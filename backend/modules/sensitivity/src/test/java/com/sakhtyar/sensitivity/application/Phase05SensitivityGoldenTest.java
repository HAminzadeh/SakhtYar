package com.sakhtyar.sensitivity.application;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase05SensitivityGoldenTest {
    private static BigDecimal d(String x) { return new BigDecimal(x); }
    private final SensitivityCalculator calculator=new SensitivityCalculator();
    private final SensitivityCalculator.Baseline baseline = new SensitivityCalculator.Baseline(
        d("300"),d("20000000"),d("0"),d("3600000000"),d("200000000"),d("100000000"),d("100000000"));

    @Test void originalEconomicsAndIndependentShocks() {
        var zero=calculator.calculate(baseline,new SensitivityCalculator.Shock(d("0"),d("0"),d("0")));
        var shocked=calculator.calculate(baseline,new SensitivityCalculator.Shock(d("10"),d("0"),d("20")));
        assertEquals(d("2000000000.00"),zero.projectedProfit());
        assertEquals(d("6600000000.00"),shocked.grossRevenue());
        assertEquals(d("4720000000.00"),shocked.totalProjectCost());
        assertEquals(d("1880000000.00"),shocked.projectedProfit());
        assertTrue(shocked.projectedProfit().compareTo(zero.projectedProfit())<0);
    }

    @Test void rejectsInvalidBaselineBeforeArithmetic() {
        assertThrows(IllegalArgumentException.class,()->calculator.calculate(null,
            new SensitivityCalculator.Shock(d("0"),d("0"),d("0"))));
        var invalid=new SensitivityCalculator.Baseline(d("300"),d("1"),d("0"),d("100"),d("-5"),d("0"),d("0"));
        assertThrows(IllegalArgumentException.class,()->calculator.calculate(invalid,
            new SensitivityCalculator.Shock(d("0"),d("0"),d("0"))));
        assertThrows(IllegalArgumentException.class,()->calculator.calculate(baseline,null));
    }
}
