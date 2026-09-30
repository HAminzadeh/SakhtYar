package com.sakhtyar.sensitivity.application;

import java.math.*;
import java.util.*;

public final class SensitivityCalculator {
    private static final BigDecimal ONE_HUNDRED=new BigDecimal("100");

    public record Baseline(
            BigDecimal sellableAreaM2,
            BigDecimal salePricePerM2,
            BigDecimal otherRevenue,
            BigDecimal baseConstructionCost,
            BigDecimal additionalCost,
            BigDecimal financingCost,
            BigDecimal taxesAndFees
    ) {}

    public record Shock(
            BigDecimal salePriceChangePercent,
            BigDecimal sellableAreaChangePercent,
            BigDecimal constructionCostChangePercent
    ) {}

    public record Point(
            BigDecimal adjustedSellableAreaM2,
            BigDecimal adjustedSalePricePerM2,
            BigDecimal adjustedBaseConstructionCost,
            BigDecimal grossRevenue,
            BigDecimal totalProjectCost,
            BigDecimal projectedProfit,
            BigDecimal roiPercent,
            BigDecimal profitMarginPercent
    ) {}

    public Point calculate(Baseline b,Shock s) {
        requireChange(s.salePriceChangePercent(),"salePriceChangePercent");
        requireChange(s.sellableAreaChangePercent(),"sellableAreaChangePercent");
        requireChange(s.constructionCostChangePercent(),"constructionCostChangePercent");

        BigDecimal area=applyChange(b.sellableAreaM2(),s.sellableAreaChangePercent(),6);
        BigDecimal price=applyChange(b.salePricePerM2(),s.salePriceChangePercent(),2);
        BigDecimal baseCost=applyChange(b.baseConstructionCost(),s.constructionCostChangePercent(),2);

        if(area.signum()<=0) throw new IllegalArgumentException("Adjusted sellable area must remain positive.");
        if(price.signum()<0 || baseCost.signum()<0) throw new IllegalArgumentException("Adjusted financial values cannot be negative.");

        BigDecimal revenue=area.multiply(price).add(b.otherRevenue()).setScale(2,RoundingMode.HALF_UP);
        BigDecimal totalCost=baseCost.add(b.additionalCost()).add(b.financingCost()).add(b.taxesAndFees())
                .setScale(2,RoundingMode.HALF_UP);
        BigDecimal profit=revenue.subtract(totalCost).setScale(2,RoundingMode.HALF_UP);
        BigDecimal roi=totalCost.signum()==0?null:profit.multiply(ONE_HUNDRED).divide(totalCost,6,RoundingMode.HALF_UP);
        BigDecimal margin=revenue.signum()==0?null:profit.multiply(ONE_HUNDRED).divide(revenue,6,RoundingMode.HALF_UP);

        return new Point(area,price,baseCost,revenue,totalCost,profit,roi,margin);
    }

    private BigDecimal applyChange(BigDecimal base,BigDecimal percent,int scale) {
        return base.multiply(BigDecimal.ONE.add(percent.divide(ONE_HUNDRED,10,RoundingMode.HALF_UP)))
                .setScale(scale,RoundingMode.HALF_UP);
    }

    private void requireChange(BigDecimal value,String name) {
        if(value==null) throw new IllegalArgumentException(name+" is required.");
        if(value.compareTo(new BigDecimal("-99.999999"))<0 || value.compareTo(new BigDecimal("1000"))>0)
            throw new IllegalArgumentException(name+" must be between -99.999999 and 1000.");
    }
}