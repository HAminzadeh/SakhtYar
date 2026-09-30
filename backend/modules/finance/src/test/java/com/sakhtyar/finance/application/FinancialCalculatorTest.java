package com.sakhtyar.finance.application;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinancialCalculatorTest {
    private final FinancialCalculator calculator=new FinancialCalculator();

    @Test
    void calculatesRevenueCostProfitAndReturns(){
        var result=calculator.calculate(
                new FinancialCalculator.Input(
                        new BigDecimal("1000"),new BigDecimal("100"),
                        BigDecimal.ZERO,new BigDecimal("60000"),
                        new BigDecimal("5000"),new BigDecimal("2000"),new BigDecimal("3000")
                ),
                List.of(
                        new FinancialCalculator.Participant("owner",new BigDecimal("45"),BigDecimal.ZERO),
                        new FinancialCalculator.Participant("builder",new BigDecimal("55"),new BigDecimal("100"))
                )
        );

        assertEquals(0,result.grossRevenue().compareTo(new BigDecimal("100000.00")));
        assertEquals(0,result.totalProjectCost().compareTo(new BigDecimal("70000.00")));
        assertEquals(0,result.projectedProfit().compareTo(new BigDecimal("30000.00")));
        assertEquals(0,result.roiPercent().compareTo(new BigDecimal("42.857143")));
    }

    @Test
    void allocatesValueAndCostSharesSeparately(){
        var result=calculator.calculate(
                new FinancialCalculator.Input(
                        new BigDecimal("1000"),new BigDecimal("100"),
                        BigDecimal.ZERO,new BigDecimal("60000"),
                        new BigDecimal("5000"),new BigDecimal("2000"),new BigDecimal("3000")
                ),
                List.of(
                        new FinancialCalculator.Participant("owner",new BigDecimal("45"),BigDecimal.ZERO),
                        new FinancialCalculator.Participant("builder",new BigDecimal("55"),new BigDecimal("100"))
                )
        );

        var owner=result.allocations().get(0);
        var builder=result.allocations().get(1);

        assertEquals(0,owner.allocatedRevenue().compareTo(new BigDecimal("45000.00")));
        assertEquals(0,owner.allocatedCost().compareTo(new BigDecimal("0.00")));
        assertEquals(0,builder.allocatedRevenue().compareTo(new BigDecimal("55000.00")));
        assertEquals(0,builder.allocatedCost().compareTo(new BigDecimal("70000.00")));
    }

    @Test
    void rejectsShareTotalsOtherThanOneHundred(){
        assertThrows(IllegalArgumentException.class,()->calculator.calculate(
                new FinancialCalculator.Input(
                        new BigDecimal("1000"),new BigDecimal("100"),
                        BigDecimal.ZERO,new BigDecimal("60000"),
                        BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO
                ),
                List.of(
                        new FinancialCalculator.Participant("a",new BigDecimal("40"),new BigDecimal("50")),
                        new FinancialCalculator.Participant("b",new BigDecimal("50"),new BigDecimal("50"))
                )
        ));
    }
}