package com.sakhtyar.regulation.application;

import com.sakhtyar.regulation.domain.*;
import java.math.*;
import java.util.Map;

public final class UrbanRuleEvaluator {

    public record EvaluationInput(
            BigDecimal landAreaM2,
            BigDecimal frontageM,
            BigDecimal passageWidthM,
            Integer proposedFloors,
            BigDecimal totalBuiltAreaM2,
            BigDecimal footprintAreaM2,
            BigDecimal proposedHeightM
    ) {}

    public record Result(
            EvaluationOutcome outcome,
            String actualValue,
            String expectedValue,
            String message
    ) {}

    public Result evaluate(UrbanRuleType type, Map<String,Object> parameters, EvaluationInput input) {
        return switch (type) {
            case MAX_FLOORS -> maxFloors(parameters, input);
            case MAX_FAR -> maxFar(parameters, input);
            case MAX_COVERAGE_PERCENT -> maxCoverage(parameters, input);
            case MIN_PASSAGE_WIDTH -> minPassage(parameters, input);
            case MIN_FRONTAGE -> minFrontage(parameters, input);
            case MAX_HEIGHT -> maxHeight(parameters, input);
        };
    }

    private Result maxFloors(Map<String,Object> p, EvaluationInput i) {
        BigDecimal limit = number(p, "maxFloors");
        if (limit == null) return review("Missing parameter maxFloors.");
        if (i.proposedFloors() == null) return review("Proposed floor count is missing.");
        BigDecimal actual = BigDecimal.valueOf(i.proposedFloors());
        return compareMax(actual, limit, "floors");
    }

    private Result maxFar(Map<String,Object> p, EvaluationInput i) {
        BigDecimal limit = number(p, "maxFar");
        if (limit == null) return review("Missing parameter maxFar.");
        if (i.landAreaM2() == null || i.landAreaM2().signum() <= 0 || i.totalBuiltAreaM2() == null) {
            return review("Land area or total built area is missing for FAR evaluation.");
        }
        BigDecimal actual = i.totalBuiltAreaM2().divide(i.landAreaM2(), 6, RoundingMode.HALF_UP);
        return compareMax(actual, limit, "FAR");
    }

    private Result maxCoverage(Map<String,Object> p, EvaluationInput i) {
        BigDecimal limit = number(p, "maxCoveragePercent");
        if (limit == null) return review("Missing parameter maxCoveragePercent.");
        if (i.landAreaM2() == null || i.landAreaM2().signum() <= 0 || i.footprintAreaM2() == null) {
            return review("Land area or footprint area is missing for coverage evaluation.");
        }
        BigDecimal actual = i.footprintAreaM2()
                .multiply(new BigDecimal("100"))
                .divide(i.landAreaM2(), 4, RoundingMode.HALF_UP);
        return compareMax(actual, limit, "coveragePercent");
    }

    private Result minPassage(Map<String,Object> p, EvaluationInput i) {
        BigDecimal limit = number(p, "minPassageWidthM");
        if (limit == null) return review("Missing parameter minPassageWidthM.");
        if (i.passageWidthM() == null) return review("Passage width is missing.");
        return compareMin(i.passageWidthM(), limit, "passageWidthM");
    }

    private Result minFrontage(Map<String,Object> p, EvaluationInput i) {
        BigDecimal limit = number(p, "minFrontageM");
        if (limit == null) return review("Missing parameter minFrontageM.");
        if (i.frontageM() == null) return review("Frontage is missing.");
        return compareMin(i.frontageM(), limit, "frontageM");
    }

    private Result maxHeight(Map<String,Object> p, EvaluationInput i) {
        BigDecimal limit = number(p, "maxHeightM");
        if (limit == null) return review("Missing parameter maxHeightM.");
        if (i.proposedHeightM() == null) return review("Proposed height is missing.");
        return compareMax(i.proposedHeightM(), limit, "heightM");
    }

    private Result compareMax(BigDecimal actual, BigDecimal expected, String label) {
        boolean pass = actual.compareTo(expected) <= 0;
        return new Result(
                pass ? EvaluationOutcome.PASS : EvaluationOutcome.FAIL,
                actual.stripTrailingZeros().toPlainString(),
                "<= " + expected.stripTrailingZeros().toPlainString(),
                pass ? label + " is within maximum allowed value." : label + " exceeds maximum allowed value."
        );
    }

    private Result compareMin(BigDecimal actual, BigDecimal expected, String label) {
        boolean pass = actual.compareTo(expected) >= 0;
        return new Result(
                pass ? EvaluationOutcome.PASS : EvaluationOutcome.FAIL,
                actual.stripTrailingZeros().toPlainString(),
                ">= " + expected.stripTrailingZeros().toPlainString(),
                pass ? label + " satisfies minimum required value." : label + " is below minimum required value."
        );
    }

    private Result review(String message) {
        return new Result(EvaluationOutcome.REVIEW, null, null, message);
    }

    private BigDecimal number(Map<String,Object> p, String key) {
        if (p == null) return null;
        Object value = p.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal b) return b;
        if (value instanceof Number n) return new BigDecimal(n.toString());
        try { return new BigDecimal(value.toString()); } catch (NumberFormatException ex) { return null; }
    }
}