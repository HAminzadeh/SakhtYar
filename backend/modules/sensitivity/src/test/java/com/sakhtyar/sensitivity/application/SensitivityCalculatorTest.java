package com.sakhtyar.sensitivity.application;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SensitivityCalculatorTest {
    private final SensitivityCalculator calculator=new SensitivityCalculator();

    private SensitivityCalculator.Baseline baseline() {
        return new SensitivityCalculator.Baseline(
                new BigDecimal("1000"),new BigDecimal("100"),
                BigDecimal.ZERO,new BigDecimal("60000"),
                new BigDecimal("5000"),new BigDecimal("2000"),new BigDecimal("3000"));
    }

    @Test
    void zeroShockMatchesBaselineEconomics(){
        var p=calculator.calculate(baseline(),new SensitivityCalculator.Shock(
                BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));
        assertEquals(0,p.grossRevenue().compareTo(new BigDecimal("100000.00")));
        assertEquals(0,p.totalProjectCost().compareTo(new BigDecimal("70000.00")));
        assertEquals(0,p.projectedProfit().compareTo(new BigDecimal("30000.00")));
    }

    @Test
    void negativeSalePriceShockReducesProfit(){
        var base=calculator.calculate(baseline(),new SensitivityCalculator.Shock(
                BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));
        var downside=calculator.calculate(baseline(),new SensitivityCalculator.Shock(
                new BigDecimal("-10"),BigDecimal.ZERO,BigDecimal.ZERO));
        assertTrue(downside.projectedProfit().compareTo(base.projectedProfit())<0);
    }

    @Test
    void constructionCostShockIncreasesTotalCost(){
        var base=calculator.calculate(baseline(),new SensitivityCalculator.Shock(
                BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));
        var downside=calculator.calculate(baseline(),new SensitivityCalculator.Shock(
                BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("20")));
        assertTrue(downside.totalProjectCost().compareTo(base.totalProjectCost())>0);
    }

    @Test
    void areaShockChangesRevenue(){
        var p=calculator.calculate(baseline(),new SensitivityCalculator.Shock(
                BigDecimal.ZERO,new BigDecimal("10"),BigDecimal.ZERO));
        assertEquals(0,p.adjustedSellableAreaM2().compareTo(new BigDecimal("1100.000000")));
        assertEquals(0,p.grossRevenue().compareTo(new BigDecimal("110000.00")));
    }
}