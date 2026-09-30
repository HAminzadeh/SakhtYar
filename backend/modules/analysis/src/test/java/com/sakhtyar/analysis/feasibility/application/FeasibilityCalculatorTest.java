package com.sakhtyar.analysis.feasibility.application;

import static org.junit.jupiter.api.Assertions.*;
import com.sakhtyar.analysis.feasibility.domain.*;
import com.sakhtyar.regulation.domain.EvaluationStatus;
import com.sakhtyar.scenario.domain.ScenarioStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FeasibilityCalculatorTest {
    private final FeasibilityCalculator calculator=new FeasibilityCalculator();

    @Test
    void compliantReadyScenarioIsFeasible(){
        var result=calculator.calculate(new FeasibilityCalculator.Input(
                EvaluationStatus.COMPLIANT,ScenarioStatus.READY,
                new BigDecimal("200"),new BigDecimal("600"),new BigDecimal("9000000000"),5));
        assertEquals(FeasibilityStatus.FEASIBLE,result.status());
        assertEquals(new BigDecimal("100.00"),result.readinessScore());
        assertEquals(new BigDecimal("3.000000"),result.calculatedFar());
    }

    @Test
    void regulationFailureIsBlocking(){
        var result=calculator.calculate(new FeasibilityCalculator.Input(
                EvaluationStatus.NON_COMPLIANT,ScenarioStatus.READY,
                new BigDecimal("200"),new BigDecimal("600"),new BigDecimal("9000000000"),5));
        assertEquals(FeasibilityStatus.NOT_FEASIBLE,result.status());
        assertTrue(result.reasons().stream().anyMatch(r->r.type()==FeasibilityReasonType.BLOCKER));
    }

    @Test
    void regulationReviewProducesConditionalOutcome(){
        var result=calculator.calculate(new FeasibilityCalculator.Input(
                EvaluationStatus.REVIEW_REQUIRED,ScenarioStatus.READY,
                new BigDecimal("200"),new BigDecimal("600"),new BigDecimal("9000000000"),5));
        assertEquals(FeasibilityStatus.CONDITIONAL,result.status());
    }

    @Test
    void missingBuiltAreaProducesInsufficientData(){
        var result=calculator.calculate(new FeasibilityCalculator.Input(
                EvaluationStatus.COMPLIANT,ScenarioStatus.READY,
                new BigDecimal("200"),null,new BigDecimal("9000000000"),5));
        assertEquals(FeasibilityStatus.INSUFFICIENT_DATA,result.status());
        assertNull(result.costPerBuiltM2());
    }
}