package com.sakhtyar.regulation.application;

import static org.junit.jupiter.api.Assertions.*;
import com.sakhtyar.regulation.domain.*;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Phase17BExactArithmeticTest {
    private final UrbanRuleEvaluator evaluator=new UrbanRuleEvaluator();
    private static BigDecimal d(String x){return x==null?null:new BigDecimal(x);}
    private static UrbanRuleEvaluator.EvaluationInput input(String land,String frontage,String passage,
            Integer floors,String total,String footprint,String height){
        return new UrbanRuleEvaluator.EvaluationInput(d(land),d(frontage),d(passage),floors,d(total),d(footprint),d(height));
    }
    @Test void farBeyondLimitIsFailEvenWhenDisplayedValueRoundsDown() {
        var result=evaluator.evaluate(UrbanRuleType.MAX_FAR,Map.of("maxFar","2.5"),
            input("1",null,null,null,"2.50000000001",null,null));
        assertEquals(EvaluationOutcome.FAIL,result.outcome());
        assertEquals("2.5",result.actualValue());
    }
    @Test void coverageBeyondLimitFailsEvenWhenDisplayRoundsDown() {
        var result=evaluator.evaluate(UrbanRuleType.MAX_COVERAGE_PERCENT,
            Map.of("maxCoveragePercent","60"),input("100",null,null,null,null,"60.000000001",null));
        assertEquals(EvaluationOutcome.FAIL,result.outcome());
        assertEquals("60",result.actualValue());
    }
    @Test void exactFarBoundaryPasses() {
        assertEquals(EvaluationOutcome.PASS,evaluator.evaluate(UrbanRuleType.MAX_FAR,
            Map.of("maxFar","2.5"),input("100",null,null,null,"250",null,null)).outcome());
    }
    @Test void negativeTotalAreaNeverPasses() {
        assertEquals(EvaluationOutcome.REVIEW,evaluator.evaluate(UrbanRuleType.MAX_FAR,
            Map.of("maxFar","2.5"),input("100",null,null,null,"-1",null,null)).outcome());
    }
    @Test void invalidCoveragePercentageRequiresReview() {
        assertEquals(EvaluationOutcome.REVIEW,evaluator.evaluate(UrbanRuleType.MAX_COVERAGE_PERCENT,
            Map.of("maxCoveragePercent","101"),input("100",null,null,null,null,"50",null)).outcome());
    }
    @Test void fractionalFloorsAreNotValidAsIntegerLimits() {
        assertEquals(EvaluationOutcome.REVIEW,evaluator.evaluate(UrbanRuleType.MAX_FLOORS,
            Map.of("maxFloors","5.5"),input(null,null,null,5,null,null,null)).outcome());
    }
    @Test void negativeFrontageRequiresReview() {
        assertEquals(EvaluationOutcome.REVIEW,evaluator.evaluate(UrbanRuleType.MIN_FRONTAGE,
            Map.of("minFrontageM","10"),input(null,"-1",null,null,null,null,null)).outcome());
    }
    @Test void conditionsAreNotSilentlyIgnored() {
        assertEquals(EvaluationOutcome.REVIEW,evaluator.evaluate(UrbanRuleType.MAX_HEIGHT,
            Map.of("maxHeightM","20","exceptions",Map.of("narrowRoad",true)),
            input(null,null,null,null,null,null,"10")).outcome());
    }
    @Test void allSixTypesHaveDeterministicValidExamples() {
        var i=input("200","12","9",4,"400","110","14");
        assertEquals(EvaluationOutcome.PASS,evaluator.evaluate(UrbanRuleType.MAX_FLOORS,Map.of("maxFloors",5),i).outcome());
        assertEquals(EvaluationOutcome.PASS,evaluator.evaluate(UrbanRuleType.MAX_FAR,Map.of("maxFar",3),i).outcome());
        assertEquals(EvaluationOutcome.PASS,evaluator.evaluate(UrbanRuleType.MAX_COVERAGE_PERCENT,Map.of("maxCoveragePercent",60),i).outcome());
        assertEquals(EvaluationOutcome.PASS,evaluator.evaluate(UrbanRuleType.MIN_PASSAGE_WIDTH,Map.of("minPassageWidthM",8),i).outcome());
        assertEquals(EvaluationOutcome.PASS,evaluator.evaluate(UrbanRuleType.MIN_FRONTAGE,Map.of("minFrontageM",10),i).outcome());
        assertEquals(EvaluationOutcome.PASS,evaluator.evaluate(UrbanRuleType.MAX_HEIGHT,Map.of("maxHeightM",20),i).outcome());
    }
}
