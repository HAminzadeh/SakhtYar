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
        if (input == null) throw new IllegalArgumentException("Financial input is required.");
        requirePositive(input.sellableAreaM2(),"sellableAreaM2");
        requireNonNegative(input.expectedSalePricePerM2(),"expectedSalePricePerM2");
        requireNonNegative(input.otherRevenue(),"otherRevenue");
        requireNonNegative(input.baseConstructionCost(),"baseConstructionCost");
        requireNonNegative(input.additionalCost(),"additionalCost");
        requireNonNegative(input.financingCost(),"financingCost");
        requireNonNegative(input.taxesAndFees(),"taxesAndFees");

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

        // Allocate in stable share/key order, independent of input order. The largest
        // share receives the bounded residual; present in caller order for API compatibility.
        List<Participant> ordered=participants.stream()
                .sorted(Comparator.comparing(Participant::valueSharePercent)
                        .thenComparing(Participant::costSharePercent)
                        .thenComparing(p->p.key().trim())).toList();
        Map<String,ParticipantResult> byKey=new HashMap<>();
        BigDecimal allocatedRevenue=BigDecimal.ZERO;
        BigDecimal allocatedCost=BigDecimal.ZERO;
        for(int index=0;index<ordered.size();index++) {
            Participant p=ordered.get(index);
            boolean last=index==ordered.size()-1;
            BigDecimal revenue=last ? grossRevenue.subtract(allocatedRevenue)
                    : grossRevenue.multiply(p.valueSharePercent())
                        .divide(new BigDecimal("100"),2,RoundingMode.HALF_UP)
                        .min(grossRevenue.subtract(allocatedRevenue));
            BigDecimal cost=last ? totalCost.subtract(allocatedCost)
                    : totalCost.multiply(p.costSharePercent())
                        .divide(new BigDecimal("100"),2,RoundingMode.HALF_UP)
                        .min(totalCost.subtract(allocatedCost));
            allocatedRevenue=allocatedRevenue.add(revenue);
            allocatedCost=allocatedCost.add(cost);
            byKey.put(p.key().trim(),new ParticipantResult(p.key(),revenue,cost,
                    revenue.subtract(cost).setScale(2,RoundingMode.HALF_UP)));
        }
        List<ParticipantResult> allocations=participants.stream()
                .map(p->byKey.get(p.key().trim())).toList();
        return new Result(grossRevenue,totalCost,profit,roi,margin,allocations);
    }

    private void validateShares(List<Participant> participants) {
        if(participants==null || participants.isEmpty())
            throw new IllegalArgumentException("At least one participant is required.");
        if(participants.size()>1000)
            throw new IllegalArgumentException("At most 1000 financial participants are supported.");

        Set<String> participantKeys=new HashSet<>();
        BigDecimal valueTotal=BigDecimal.ZERO;
        BigDecimal costTotal=BigDecimal.ZERO;

        for(var p:participants) {
            if(p==null || p.key()==null || p.key().isBlank() || !participantKeys.add(p.key().trim()))
                throw new IllegalArgumentException("Each participant requires a unique nonblank key.");
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

    private void requirePositive(BigDecimal value,String name) {
        requireNonNegative(value,name);
        if(value.signum()==0) throw new IllegalArgumentException(name+" must be positive.");
    }

    private void requireNonNegative(BigDecimal value,String name) {
        if(value==null || value.signum()<0) throw new IllegalArgumentException(name+" must be non-negative.");
        requireBoundedDecimal(value,name);
    }

    private void requirePercent(BigDecimal value,String name) {
        if(value==null || value.signum()<0 || value.compareTo(new BigDecimal("100"))>0)
            throw new IllegalArgumentException(name+" must be between 0 and 100.");
        requireBoundedDecimal(value,name);
    }

    private void requireBoundedDecimal(BigDecimal value,String name) {
        if(value.precision()>38 || value.scale()>12 || ((long)value.precision()-value.scale())>30)
            throw new IllegalArgumentException(name+" exceeds 30 integer digits or 12 decimal places.");
    }
}