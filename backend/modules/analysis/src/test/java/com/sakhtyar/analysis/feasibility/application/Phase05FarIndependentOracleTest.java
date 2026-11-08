package com.sakhtyar.analysis.feasibility.application;

import static org.junit.jupiter.api.Assertions.*;
import com.sakhtyar.regulation.domain.EvaluationStatus;
import com.sakhtyar.scenario.domain.ScenarioStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class Phase05FarIndependentOracleTest {
    private static BigDecimal d(String s){return new BigDecimal(s);}
    @Test void exactSyntheticFarAndCostPerBuiltArea(){
        var result=new FeasibilityCalculator().calculate(new FeasibilityCalculator.Input(
                EvaluationStatus.COMPLIANT,ScenarioStatus.READY,d("300"),d("900"),d("900000000"),3));
        assertEquals(d("3.000000"),result.calculatedFar());
        assertEquals(d("1000000.00"),result.costPerBuiltM2());
    }
    @Test void zeroLandAreaPreventsDividingAndRecordsWarning(){
        var result=new FeasibilityCalculator().calculate(new FeasibilityCalculator.Input(
                EvaluationStatus.COMPLIANT,ScenarioStatus.READY,d("0"),d("900"),d("900000000"),3));
        assertNull(result.calculatedFar());
        assertTrue(result.reasons().stream().anyMatch(r->"LAND_AREA_MISSING".equals(r.code())));
    }
}