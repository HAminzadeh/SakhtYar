package com.sakhtyar.finance.application;

import java.math.*;
import java.util.*;

public final class FinancialCalculator {

    public record Input(
            BigDecimal sellableAreaM2,
            BigDecimal expectedSalePricePerM2,
            BigDecimal otherRevenue,
            BigDecimal baseConstructionCost,
            BigDecimal additionalCost,
            BigDecimal financingCost,
            BigDecimal taxesAndFees
    ) {}

    public record Participant(
            String key,
            BigDecimal valueSharePercent,
            BigDecimal costSharePercent
    ) {}

    public record ParticipantResult(
            String key,
            BigDecimal allocatedRevenue,
            BigDecimal allocatedCost,
            BigDecimal projectedNetValue
    ) {}

    public record Result(
            BigDecimal grossRevenue,
            BigDecimal totalProjectCost,
            BigDecimal projectedProfit,
            BigDecimal roiPercent,
            BigDecimal profitMarginPercent,
            List<ParticipantResult> allocations
    ) {}

    public Result calculate(Input input,List<Participant> participants) {
        requireNonNegative(input.expectedSalePricePerM2(),"expectedSalePricePerM2");
        requireNonNegative(input.otherRevenue(),"otherRevenue");
        requireNonNegative(input.baseConstructionCost(),"baseConstructionCost");
        requireNonNegative(input.additionalCost(),"additionalCost");
        requireNonNegative(input.financingCost(),"financingCost");
        requireNonNegative(input.taxesAndFees(),"taxesAndFees");

        if(input.sellableAreaM2()==null || input.sellableAreaM2().signum()<=0)
            throw new IllegalArgumentException("sellableAreaM2 must be positive.");

        validateShares(participants);

        BigDecimal grossRevenue=input.sellableAreaM2()
                .multiply(input.expectedSalePricePerM2())
                .add(input.otherRevenue())
                .setScale(2,RoundingMode.HALF_UP);

        BigDecimal totalCost=input.baseConstructionCost()
                .add(input.additionalCost())
                .add(input.financingCost())
                .add(input.taxesAndFees())
                .setScale(2,RoundingMode.HALF_UP);

        BigDecimal profit=grossRevenue.subtract(totalCost).setScale(2,RoundingMode.HALF_UP);

        BigDecimal roi=totalCost.signum()==0?null:
                profit.multiply(new BigDecimal("100")).divide(totalCost,6,RoundingMode.HALF_UP);

        BigDecimal margin=grossRevenue.signum()==0?null:
                profit.multiply(new BigDecimal("100")).divide(grossRevenue,6,RoundingMode.HALF_UP);

        List<ParticipantResult> allocations=participants.stream().map(p -> {
            BigDecimal revenue=grossRevenue.multiply(p.valueSharePercent())
                    .divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);
            BigDecimal cost=totalCost.multiply(p.costSharePercent())
                    .divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);
            return new ParticipantResult(p.key(),revenue,cost,revenue.subtract(cost).setScale(2,RoundingMode.HALF_UP));
        }).toList();

        return new Result(grossRevenue,totalCost,profit,roi,margin,allocations);
    }

    private void validateShares(List<Participant> participants) {
        if(participants==null || participants.isEmpty())
            throw new IllegalArgumentException("At least one participant is required.");

        BigDecimal valueTotal=BigDecimal.ZERO;
        BigDecimal costTotal=BigDecimal.ZERO;

        for(var p:participants) {
            requirePercent(p.valueSharePercent(),"valueSharePercent");
            requirePercent(p.costSharePercent(),"costSharePercent");
            valueTotal=valueTotal.add(p.valueSharePercent());
            costTotal=costTotal.add(p.costSharePercent());
        }

        BigDecimal tolerance=new BigDecimal("0.0001");
        if(valueTotal.subtract(new BigDecimal("100")).abs().compareTo(tolerance)>0)
            throw new IllegalArgumentException("Participant value shares must total 100%.");
        if(costTotal.subtract(new BigDecimal("100")).abs().compareTo(tolerance)>0)
            throw new IllegalArgumentException("Participant cost shares must total 100%.");
    }

    private void requireNonNegative(BigDecimal value,String name) {
        if(value==null || value.signum()<0) throw new IllegalArgumentException(name+" must be non-negative.");
    }

    private void requirePercent(BigDecimal value,String name) {
        if(value==null || value.signum()<0 || value.compareTo(new BigDecimal("100"))>0)
            throw new IllegalArgumentException(name+" must be between 0 and 100.");
    }
}