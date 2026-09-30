package com.sakhtyar.analysis.feasibility.application;

import com.sakhtyar.analysis.feasibility.domain.*;
import com.sakhtyar.regulation.domain.EvaluationStatus;
import com.sakhtyar.scenario.domain.ScenarioStatus;
import java.math.*;
import java.util.*;

public final class FeasibilityCalculator {

    public record Input(
            EvaluationStatus regulationStatus,
            ScenarioStatus scenarioStatus,
            BigDecimal landAreaM2,
            BigDecimal totalBuiltAreaM2,
            BigDecimal scenarioCost,
            Integer proposedFloors
    ) {}

    public record Result(
            FeasibilityStatus status,
            BigDecimal readinessScore,
            BigDecimal costPerLandM2,
            BigDecimal costPerBuiltM2,
            BigDecimal calculatedFar,
            List<Reason> reasons
    ) {}

    public record Reason(
            FeasibilityReasonType type,
            String code,
            String message
    ) {}

    public Result calculate(Input input) {
        List<Reason> reasons=new ArrayList<>();
        int score=100;

        if(input.regulationStatus()==EvaluationStatus.NON_COMPLIANT) {
            reasons.add(new Reason(FeasibilityReasonType.BLOCKER,"REGULATION_NON_COMPLIANT",
                    "Urban regulation evaluation contains one or more failed rules."));
            score-=60;
        } else if(input.regulationStatus()==EvaluationStatus.REVIEW_REQUIRED) {
            reasons.add(new Reason(FeasibilityReasonType.WARNING,"REGULATION_REVIEW_REQUIRED",
                    "Urban regulation evaluation contains unresolved review items."));
            score-=25;
        }

        if(input.scenarioStatus()!=ScenarioStatus.READY) {
            reasons.add(new Reason(FeasibilityReasonType.WARNING,"SCENARIO_NOT_READY",
                    "Construction scenario is not in READY status."));
            score-=15;
        }

        if(input.landAreaM2()==null || input.landAreaM2().signum()<=0) {
            reasons.add(new Reason(FeasibilityReasonType.WARNING,"LAND_AREA_MISSING",
                    "Valid land area is required for normalized feasibility metrics."));
            score-=15;
        }

        if(input.totalBuiltAreaM2()==null || input.totalBuiltAreaM2().signum()<=0) {
            reasons.add(new Reason(FeasibilityReasonType.WARNING,"BUILT_AREA_MISSING",
                    "Total built area is missing; FAR and cost-per-built-area cannot be calculated."));
            score-=15;
        }

        if(input.scenarioCost()==null || input.scenarioCost().signum()<0) {
            reasons.add(new Reason(FeasibilityReasonType.BLOCKER,"SCENARIO_COST_INVALID",
                    "Scenario cost snapshot is missing or invalid."));
            score-=50;
        }

        BigDecimal costPerLand=divide(input.scenarioCost(),input.landAreaM2(),2);
        BigDecimal costPerBuilt=divide(input.scenarioCost(),input.totalBuiltAreaM2(),2);
        BigDecimal far=divide(input.totalBuiltAreaM2(),input.landAreaM2(),6);

        score=Math.max(0,Math.min(100,score));

        long blockers=reasons.stream().filter(r->r.type()==FeasibilityReasonType.BLOCKER).count();
        boolean missingCritical=input.landAreaM2()==null || input.totalBuiltAreaM2()==null || input.scenarioCost()==null;

        FeasibilityStatus status;
        if(blockers>0) status=FeasibilityStatus.NOT_FEASIBLE;
        else if(missingCritical) status=FeasibilityStatus.INSUFFICIENT_DATA;
        else if(reasons.stream().anyMatch(r->r.type()==FeasibilityReasonType.WARNING)) status=FeasibilityStatus.CONDITIONAL;
        else status=FeasibilityStatus.FEASIBLE;

        return new Result(status,new BigDecimal(score).setScale(2),costPerLand,costPerBuilt,far,List.copyOf(reasons));
    }

    private BigDecimal divide(BigDecimal numerator,BigDecimal denominator,int scale) {
        if(numerator==null || denominator==null || denominator.signum()<=0) return null;
        return numerator.divide(denominator,scale,RoundingMode.HALF_UP);
    }
}