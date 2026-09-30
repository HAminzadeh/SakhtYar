package com.sakhtyar.sensitivity.api;

import com.sakhtyar.sensitivity.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class SensitivityDtos {
    private SensitivityDtos(){}

    public record ShockRequest(
            @NotNull @DecimalMin("-99.999999") @DecimalMax("1000") BigDecimal salePriceChangePercent,
            @NotNull @DecimalMin("-99.999999") @DecimalMax("1000") BigDecimal sellableAreaChangePercent,
            @NotNull @DecimalMin("-99.999999") @DecimalMax("1000") BigDecimal constructionCostChangePercent
    ) {}

    public record CreateSensitivityRequest(
            @NotNull UUID financialAnalysisId,
            @NotEmpty @Size(max=250) List<@Valid ShockRequest> points
    ) {}

    public record PointResponse(
            UUID id,int sequenceNo,BigDecimal salePriceChangePercent,BigDecimal sellableAreaChangePercent,
            BigDecimal constructionCostChangePercent,BigDecimal adjustedSellableAreaM2,
            BigDecimal adjustedSalePricePerM2,BigDecimal adjustedBaseConstructionCost,
            BigDecimal grossRevenue,BigDecimal totalProjectCost,BigDecimal projectedProfit,
            BigDecimal roiPercent,BigDecimal profitMarginPercent,Instant createdAt
    ) {
        public static PointResponse from(SensitivityPointEntity e) {
            return new PointResponse(e.getId(),e.getSequenceNo(),e.getSalePriceChangePercent(),
                    e.getSellableAreaChangePercent(),e.getConstructionCostChangePercent(),
                    e.getAdjustedSellableAreaM2(),e.getAdjustedSalePricePerM2(),
                    e.getAdjustedBaseConstructionCost(),e.getGrossRevenue(),e.getTotalProjectCost(),
                    e.getProjectedProfit(),e.getRoiPercent(),e.getProfitMarginPercent(),e.getCreatedAt());
        }
    }

    public record SensitivityResponse(
            UUID id,UUID financialAnalysisId,UUID caseId,UUID scenarioId,String currencyCode,
            int pointCount,BigDecimal bestProfit,BigDecimal worstProfit,BigDecimal bestRoiPercent,
            BigDecimal worstRoiPercent,String algorithmVersion,Map<String,Object> inputSnapshot,
            Map<String,Object> summarySnapshot,String calculatedBy,Instant calculatedAt,
            List<PointResponse> points
    ) {}
}