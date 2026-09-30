package com.sakhtyar.finance.api;

import com.sakhtyar.finance.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class FinanceDtos {
    private FinanceDtos(){}

    public record ParticipantRequest(
            @NotNull ParticipantType participantType,
            UUID participantRefId,
            @NotBlank @Size(max=400) String participantLabel,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal valueSharePercent,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal costSharePercent,
            Map<String,Object> metadata
    ) {}

    public record CreateFinancialAnalysisRequest(
            @NotNull UUID feasibilityAssessmentId,
            @NotNull @DecimalMin(value="0.000001") BigDecimal sellableAreaM2,
            @NotNull @DecimalMin("0.0") BigDecimal expectedSalePricePerM2,
            @NotBlank @Size(min=3,max=3) String currencyCode,
            @NotNull @DecimalMin("0.0") BigDecimal otherRevenue,
            @NotNull @DecimalMin("0.0") BigDecimal additionalCost,
            @NotNull @DecimalMin("0.0") BigDecimal financingCost,
            @NotNull @DecimalMin("0.0") BigDecimal taxesAndFees,
            @NotEmpty List<@Valid ParticipantRequest> participants
    ) {}

    public record AllocationResponse(
            UUID id,ParticipantType participantType,UUID participantRefId,String participantLabel,
            BigDecimal valueSharePercent,BigDecimal costSharePercent,BigDecimal allocatedRevenue,
            BigDecimal allocatedCost,BigDecimal projectedNetValue,Map<String,Object> metadata,Instant createdAt
    ) {
        public static AllocationResponse from(FinancialParticipationAllocationEntity e) {
            return new AllocationResponse(e.getId(),e.getParticipantType(),e.getParticipantRefId(),
                    e.getParticipantLabel(),e.getValueSharePercent(),e.getCostSharePercent(),
                    e.getAllocatedRevenue(),e.getAllocatedCost(),e.getProjectedNetValue(),
                    e.getMetadata(),e.getCreatedAt());
        }
    }

    public record FinancialAnalysisResponse(
            UUID id,UUID feasibilityAssessmentId,UUID caseId,UUID propertyId,UUID scenarioId,
            String currencyCode,BigDecimal sellableAreaM2,BigDecimal expectedSalePricePerM2,
            BigDecimal otherRevenue,BigDecimal baseConstructionCost,BigDecimal additionalCost,
            BigDecimal financingCost,BigDecimal taxesAndFees,BigDecimal grossRevenue,
            BigDecimal totalProjectCost,BigDecimal projectedProfit,BigDecimal roiPercent,
            BigDecimal profitMarginPercent,String algorithmVersion,Map<String,Object> inputSnapshot,
            Map<String,Object> resultSnapshot,String calculatedBy,Instant calculatedAt,
            List<AllocationResponse> allocations
    ) {}
}