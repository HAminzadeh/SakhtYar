package com.sakhtyar.regulation.application;

import static org.junit.jupiter.api.Assertions.*;
import com.sakhtyar.regulation.domain.*;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UrbanRuleEvaluatorTest {
    private final UrbanRuleEvaluator evaluator=new UrbanRuleEvaluator();

    @Test
    void maxFloorsPassesAndFailsDeterministically(){
        var pass=evaluator.evaluate(UrbanRuleType.MAX_FLOORS,Map.of("maxFloors",5),
            new UrbanRuleEvaluator.EvaluationInput(null,null,null,4,null,null,null));
        var fail=evaluator.evaluate(UrbanRuleType.MAX_FLOORS,Map.of("maxFloors",5),
            new UrbanRuleEvaluator.EvaluationInput(null,null,null,6,null,null,null));
        assertEquals(EvaluationOutcome.PASS,pass.outcome());
        assertEquals(EvaluationOutcome.FAIL,fail.outcome());
    }

    @Test
    void farUsesBuiltAreaDividedByLandArea(){
        var result=evaluator.evaluate(UrbanRuleType.MAX_FAR,Map.of("maxFar","2.50"),
            new UrbanRuleEvaluator.EvaluationInput(
                new BigDecimal("200"),null,null,null,new BigDecimal("450"),null,null));
        assertEquals(EvaluationOutcome.PASS,result.outcome());
        assertEquals("2.25",result.actualValue());
    }

    @Test
    void missingInputRequiresReviewInsteadOfGuessing(){
        var result=evaluator.evaluate(UrbanRuleType.MIN_FRONTAGE,Map.of("minFrontageM","10"),
            new UrbanRuleEvaluator.EvaluationInput(null,null,null,null,null,null,null));
        assertEquals(EvaluationOutcome.REVIEW,result.outcome());
    }

    @Test
    void coverageIsEvaluatedAsPercentage(){
        var result=evaluator.evaluate(UrbanRuleType.MAX_COVERAGE_PERCENT,Map.of("maxCoveragePercent","60"),
            new UrbanRuleEvaluator.EvaluationInput(
                new BigDecimal("250"),null,null,null,null,new BigDecimal("150"),null));
        assertEquals(EvaluationOutcome.PASS,result.outcome());
        assertEquals("60",result.actualValue());
    }
}