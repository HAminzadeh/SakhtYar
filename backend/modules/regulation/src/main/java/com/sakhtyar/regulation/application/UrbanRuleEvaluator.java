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
        if (type == null || input == null) return review("Missing rule type or evaluation input.");
        // Do not silently ignore clauses, conditional predicates or exceptions.
        if (parameters != null && parameters.keySet().stream().anyMatch(
                k -> java.util.Set.of("conditions", "condition", "exceptions", "exceptionRuleIds",
                    "requiresManualReview", "textRequirement").contains(k)))
            return review("Conditional or textual rule requires independent legal review.");
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
        if (i.proposedFloors() == null || i.proposedFloors() < 0 || limit.signum() < 0 ||
            limit.stripTrailingZeros().scale() > 0)
            return review("Floor count or integer floor limit is invalid.");
        BigDecimal actual = BigDecimal.valueOf(i.proposedFloors());
        return compareMax(actual, limit, "floors");
    }

    private Result maxFar(Map<String,Object> p, EvaluationInput i) {
        BigDecimal limit = number(p, "maxFar");
        if (limit == null) return review("Missing parameter maxFar.");
        if (i.landAreaM2() == null || i.landAreaM2().signum() <= 0 || i.totalBuiltAreaM2() == null) {
            return review("Land area or total built area is missing for FAR evaluation.");
        }
        if (limit.signum() < 0 || i.totalBuiltAreaM2().signum() < 0)
            return review("FAR limit or built area must not be negative.");
        // The decision is exact; the divided/rounded value is display-only.
        boolean passes = i.totalBuiltAreaM2().compareTo(limit.multiply(i.landAreaM2())) <= 0;
        BigDecimal actual = i.totalBuiltAreaM2().divide(i.landAreaM2(), 6, RoundingMode.HALF_UP);
        return compareMaxExact(actual, limit, "FAR", passes);
    }

    private Result maxCoverage(Map<String,Object> p, EvaluationInput i) {
        BigDecimal limit = number(p, "maxCoveragePercent");
        if (limit == null) return review("Missing parameter maxCoveragePercent.");
        if (i.landAreaM2() == null || i.landAreaM2().signum() <= 0 || i.footprintAreaM2() == null) {
            return review("Land area or footprint area is missing for coverage evaluation.");
        }
        if (limit.signum() < 0 || limit.compareTo(new BigDecimal("100")) > 0 ||
            i.footprintAreaM2().signum() < 0)
            return review("Coverage percentage or footprint area is invalid.");
        BigDecimal product = i.footprintAreaM2().multiply(new BigDecimal("100"));
        boolean passes = product.compareTo(limit.multiply(i.landAreaM2())) <= 0;
        BigDecimal actual = product.divide(i.landAreaM2(), 4, RoundingMode.HALF_UP);
        return compareMaxExact(actual, limit, "coveragePercent", passes);
    }

    private Result minPassage(Map<String,Object> p, EvaluationInput i) {
        BigDecimal limit = number(p, "minPassageWidthM");
        if (limit == null) return review("Missing parameter minPassageWidthM.");
        if (i.passageWidthM() == null) return review("Passage width is missing.");
        if (i.passageWidthM().signum() < 0 || limit.signum() < 0)
            return review("Passage width or minimum limit is invalid.");
        return compareMin(i.passageWidthM(), limit, "passageWidthM");
    }

    private Result minFrontage(Map<String,Object> p, EvaluationInput i) {
        BigDecimal limit = number(p, "minFrontageM");
        if (limit == null) return review("Missing parameter minFrontageM.");
        if (i.frontageM() == null) return review("Frontage is missing.");
        if (i.frontageM().signum() < 0 || limit.signum() < 0)
            return review("Frontage or minimum limit is invalid.");
        return compareMin(i.frontageM(), limit, "frontageM");
    }

    private Result maxHeight(Map<String,Object> p, EvaluationInput i) {
        BigDecimal limit = number(p, "maxHeightM");
        if (limit == null) return review("Missing parameter maxHeightM.");
        if (i.proposedHeightM() == null) return review("Proposed height is missing.");
        if (i.proposedHeightM().signum() < 0 || limit.signum() < 0)
            return review("Height or maximum limit is invalid.");
        return compareMax(i.proposedHeightM(), limit, "heightM");
    }

    private Result compareMax(BigDecimal actual, BigDecimal expected, String label) {
        if (actual.signum() < 0 || expected.signum() < 0) return review("Negative value is invalid.");
        return compareMaxExact(actual, expected, label, actual.compareTo(expected) <= 0);
    }

    private Result compareMaxExact(BigDecimal actual, BigDecimal expected, String label, boolean pass) {
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