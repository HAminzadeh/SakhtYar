package com.sakhtyar.finance.application;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class Phase05FinanceDeterminismTest {
    private static BigDecimal d(String n){return new BigDecimal(n);}
    private static FinancialCalculator.Input input(String gross,String cost){
        return new FinancialCalculator.Input(d("1"),d(gross),d("0"),d(cost),d("0"),d("0"),d("0"));
    }
    @Test void independentTwoPartyGoldenAndRounding(){
        var r=new FinancialCalculator().calculate(
                new FinancialCalculator.Input(d("300"),d("20000000"),d("0"),d("3600000000"),
                        d("200000000"),d("100000000"),d("100000000")),
                List.of(new FinancialCalculator.Participant("owner",d("60"),d("40")),
                        new FinancialCalculator.Participant("builder",d("40"),d("60"))));
        assertEquals(d("6000000000.00"),r.grossRevenue());
        assertEquals(d("4000000000.00"),r.totalProjectCost());
        assertEquals(d("2000000000.00"),r.projectedProfit());
        assertEquals(d("50.000000"),r.roiPercent());
        assertEquals(d("33.333333"),r.profitMarginPercent());
        assertEquals(d("3600000000.00"),r.allocations().get(0).allocatedRevenue());
    }
    @Test void shuffledParticipantsKeepIdenticalAmountsByKey(){
        var p=List.of(new FinancialCalculator.Participant("zeta",d("33.3334"),d("33.3334")),
                new FinancialCalculator.Participant("alpha",d("33.3333"),d("33.3333")),
                new FinancialCalculator.Participant("beta",d("33.3333"),d("33.3333")));
        var c=new FinancialCalculator();
        var first=c.calculate(input("100.00","0.99"),p);
        var shuffled=c.calculate(input("100.00","0.99"),List.of(p.get(1),p.get(2),p.get(0)));
        var a=first.allocations().stream().collect(Collectors.toMap(FinancialCalculator.ParticipantResult::key,
                FinancialCalculator.ParticipantResult::allocatedRevenue));
        var b=shuffled.allocations().stream().collect(Collectors.toMap(FinancialCalculator.ParticipantResult::key,
                FinancialCalculator.ParticipantResult::allocatedRevenue));
        assertEquals(a,b);
        assertEquals(d("100.00"),a.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add));
        assertEquals(d("0.99"),first.allocations().stream().map(FinancialCalculator.ParticipantResult::allocatedCost)
                .reduce(BigDecimal.ZERO,BigDecimal::add));
    }
    @Test void invalidInputsFailBeforeProcessing(){
        var c=new FinancialCalculator();
        var p=List.of(new FinancialCalculator.Participant("owner",d("100"),d("100")));
        assertThrows(IllegalArgumentException.class,()->c.calculate(null,p));
        assertThrows(IllegalArgumentException.class,()->c.calculate(input("1E+40","1"),p));
        assertThrows(IllegalArgumentException.class,()->c.calculate(input("1","-1"),p));
        assertThrows(IllegalArgumentException.class,()->c.calculate(input("1","1"),
                List.of(new FinancialCalculator.Participant("x",d("50"),d("50")),
                        new FinancialCalculator.Participant("x",d("50"),d("50")))));
    }
    @Test void zeroCostRoiRemainsUndefined(){
        var r=new FinancialCalculator().calculate(input("1.00","0"),
                List.of(new FinancialCalculator.Participant("a",d("100"),d("100"))));
        assertNull(r.roiPercent());
        assertEquals(d("100.000000"),r.profitMarginPercent());
    }
}