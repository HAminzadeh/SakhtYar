package com.sakhtyar.finance.application;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase05FinancialConservationTest {
    private static BigDecimal d(String x) { return new BigDecimal(x); }

    @Test void threeWayRoundingConservesEachCurrencyMinorUnit() {
        var result = new FinancialCalculator().calculate(
            new FinancialCalculator.Input(d("1"), d("100.00"), d("0"),
                d("0.99"), d("0"), d("0"), d("0")),
            List.of(
                new FinancialCalculator.Participant("one",d("33.3333"),d("33.3333")),
                new FinancialCalculator.Participant("two",d("33.3333"),d("33.3333")),
                new FinancialCalculator.Participant("three",d("33.3334"),d("33.3334"))
            ));
        assertEquals(d("33.33"), result.allocations().get(0).allocatedRevenue());
        assertEquals(d("33.33"), result.allocations().get(1).allocatedRevenue());
        assertEquals(d("33.34"), result.allocations().get(2).allocatedRevenue());
        assertEquals(d("0.33"), result.allocations().get(2).allocatedCost());
        BigDecimal revenue = result.allocations().stream().map(FinancialCalculator.ParticipantResult::allocatedRevenue)
            .reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal cost = result.allocations().stream().map(FinancialCalculator.ParticipantResult::allocatedCost)
            .reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal net = result.allocations().stream().map(FinancialCalculator.ParticipantResult::projectedNetValue)
            .reduce(BigDecimal.ZERO,BigDecimal::add);
        assertEquals(0, result.grossRevenue().compareTo(revenue));
        assertEquals(0, result.totalProjectCost().compareTo(cost));
        assertEquals(0, result.projectedProfit().compareTo(net));
    }

    @Test void tinyTotalNeverCreatesNegativeRoundingResidual() {
        var r=new FinancialCalculator().calculate(
            new FinancialCalculator.Input(d("1"),d("0.03"),d("0"),d("0.03"),
                d("0"),d("0"),d("0")),
            List.of(new FinancialCalculator.Participant("a",d("16.6666"),d("16.6666")),
                    new FinancialCalculator.Participant("b",d("16.6666"),d("16.6666")),
                    new FinancialCalculator.Participant("c",d("16.6666"),d("16.6666")),
                    new FinancialCalculator.Participant("d",d("16.6666"),d("16.6666")),
                    new FinancialCalculator.Participant("e",d("16.6666"),d("16.6666")),
                    new FinancialCalculator.Participant("f",d("16.6670"),d("16.6670"))));
        assertEquals(0,d("0.03").compareTo(r.allocations().stream()
            .map(FinancialCalculator.ParticipantResult::allocatedRevenue)
            .reduce(BigDecimal.ZERO,BigDecimal::add)));
        assertTrue(r.allocations().stream().allMatch(a->a.allocatedRevenue().signum()>=0 && a.allocatedCost().signum()>=0));
    }

    @Test void independentGoldenForTwoParticipantRealisticProject() {
        var r = new FinancialCalculator().calculate(
            new FinancialCalculator.Input(d("300"),d("20000000"),d("0"),
                d("3600000000"),d("200000000"),d("100000000"),d("100000000")),
            List.of(new FinancialCalculator.Participant("owner",d("60"),d("40")),
                    new FinancialCalculator.Participant("builder",d("40"),d("60"))));
        assertEquals(d("6000000000.00"),r.grossRevenue());
        assertEquals(d("4000000000.00"),r.totalProjectCost());
        assertEquals(d("2000000000.00"),r.projectedProfit());
        assertEquals(d("50.000000"),r.roiPercent());
        assertEquals(d("3600000000.00"),r.allocations().get(0).allocatedRevenue());
        assertEquals(d("1600000000.00"),r.allocations().get(0).allocatedCost());
        assertEquals(d("2000000000.00"),r.allocations().get(0).projectedNetValue());
    }
}
