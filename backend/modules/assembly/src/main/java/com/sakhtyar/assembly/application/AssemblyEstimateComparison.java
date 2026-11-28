package com.sakhtyar.assembly.application;

import com.sakhtyar.assembly.api.AssemblyDtos.EstimateResponse;
import com.sakhtyar.assembly.api.AssemblyDtos.EstimateLineResponse;
import java.math.BigDecimal;
import java.util.*;

/** Compare stored results only, never re-resolve live rates. Decimals on this API are strings. */
public final class AssemblyEstimateComparison {
    private AssemblyEstimateComparison() {}
    public record Lineage(UUID estimateId, UUID recalculatedFromId, String algorithmVersion) {}
    public record Line(UUID componentId, String change, UUID beforeMaterialId, UUID afterMaterialId,
            UUID beforeVariantId, UUID afterVariantId, UUID beforeAggregateId, UUID afterAggregateId,
            String beforeUnit, String afterUnit, String beforeQuantity, String afterQuantity,
            String beforeUnitPrice, String afterUnitPrice, String beforePriceBasis, String afterPriceBasis,
            String beforeTotal, String afterTotal, String delta) {}
    public record Result(UUID beforeEstimateId, UUID afterEstimateId, UUID assemblyId, String currency,
            String outputUnit, String beforeAlgorithm, String afterAlgorithm,
            String beforeQuantity, String afterQuantity, String beforeTotal, String afterTotal,
            String totalDelta, String beforeUnitCost, String afterUnitCost, String unitCostDelta, List<Line> lines) {}

    public static Result compare(EstimateResponse before, EstimateResponse after) {
        if (!Objects.equals(before.assemblyId(),after.assemblyId())
                || !Objects.equals(before.currencyCode(),after.currencyCode())
                || !Objects.equals(before.outputUnitCode(),after.outputUnitCode())
                || before.priceType()!=after.priceType()
                || !Objects.equals(before.province(),after.province()) || !Objects.equals(before.city(),after.city()))
            throw new IllegalArgumentException("Estimates must share assembly, currency, unit and price market context.");
        Map<UUID,EstimateLineResponse> a=index(before.lines()), b=index(after.lines());
        TreeSet<UUID> ids=new TreeSet<>(a.keySet()); ids.addAll(b.keySet());
        List<Line> rows=new ArrayList<>();
        for(UUID id:ids) {
            var x=a.get(id); var y=b.get(id);
            String change=x==null?"ADDED":y==null?"REMOVED":same(x,y)?"UNCHANGED":"CHANGED";
            rows.add(new Line(id,change,x==null?null:x.materialItemId(),y==null?null:y.materialItemId(),
                x==null?null:x.variantId(),y==null?null:y.variantId(),x==null?null:x.aggregateId(),y==null?null:y.aggregateId(),
                x==null?null:x.unitCode(),y==null?null:y.unitCode(),
                x==null?null:plain(x.effectiveQuantity()),y==null?null:plain(y.effectiveQuantity()),
                x==null?null:plain(x.unitPrice()),y==null?null:plain(y.unitPrice()),
                x==null?null:x.priceBasis().name(),y==null?null:y.priceBasis().name(),
                x==null?null:plain(x.lineTotal()),y==null?null:plain(y.lineTotal()),
                delta(x==null?BigDecimal.ZERO:x.lineTotal(),y==null?BigDecimal.ZERO:y.lineTotal())));
        }
        return new Result(before.id(),after.id(),before.assemblyId(),before.currencyCode(),before.outputUnitCode(),
            before.algorithmVersion(),after.algorithmVersion(),plain(before.requestedQuantity()),plain(after.requestedQuantity()),
            plain(before.totalCost()),plain(after.totalCost()),delta(before.totalCost(),after.totalCost()),
            plain(before.unitCost()),plain(after.unitCost()),delta(before.unitCost(),after.unitCost()),List.copyOf(rows));
    }
    private static Map<UUID,EstimateLineResponse> index(List<EstimateLineResponse> lines) {
        Map<UUID,EstimateLineResponse> result=new HashMap<>();
        for(var row:lines) if(result.putIfAbsent(row.assemblyComponentId(),row)!=null)
            throw new IllegalStateException("Estimate contains duplicate component lines.");
        return result;
    }
    private static boolean same(EstimateLineResponse x,EstimateLineResponse y) {
        return Objects.equals(x.materialItemId(),y.materialItemId()) && Objects.equals(x.variantId(),y.variantId())
            && Objects.equals(x.aggregateId(),y.aggregateId()) && Objects.equals(x.unitCode(),y.unitCode())
            && x.priceBasis()==y.priceBasis() && x.baseQuantity().compareTo(y.baseQuantity())==0
            && x.wasteFactor().compareTo(y.wasteFactor())==0 && x.effectiveQuantity().compareTo(y.effectiveQuantity())==0
            && x.unitPrice().compareTo(y.unitPrice())==0 && x.lineTotal().compareTo(y.lineTotal())==0;
    }
    private static String plain(BigDecimal v){return v.toPlainString();}
    private static String delta(BigDecimal a,BigDecimal b){return plain(b.subtract(a));}
}
