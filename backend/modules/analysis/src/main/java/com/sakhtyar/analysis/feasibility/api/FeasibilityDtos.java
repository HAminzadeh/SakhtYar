package com.sakhtyar.analysis.feasibility.api;

import com.sakhtyar.analysis.feasibility.domain.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class FeasibilityDtos {
    private FeasibilityDtos(){}

    public record CreateAssessmentRequest(
            @NotNull UUID caseId,
            @NotNull UUID scenarioId,
            @NotNull UUID scenarioCostSnapshotId,
            @NotNull UUID urbanEvaluationId
    ) {}

    public record ReasonResponse(
            UUID id,FeasibilityReasonType reasonType,String reasonCode,String message,
            String sourceEntityType,UUID sourceEntityId,Map<String,Object> details,Instant createdAt
    ) {
        public static ReasonResponse from(FeasibilityReasonEntity e) {
            return new ReasonResponse(e.getId(),e.getReasonType(),e.getReasonCode(),e.getMessage(),
                    e.getSourceEntityType(),e.getSourceEntityId(),e.getDetails(),e.getCreatedAt());
        }
    }

    public record AssessmentResponse(
            UUID id,UUID caseId,UUID propertyId,UUID scenarioId,UUID scenarioCostSnapshotId,
            UUID urbanEvaluationId,FeasibilityStatus status,BigDecimal readinessScore,
            BigDecimal scenarioCost,String costCurrencyCode,BigDecimal landAreaM2,
            BigDecimal totalBuiltAreaM2,BigDecimal costPerLandM2,BigDecimal costPerBuiltM2,
            Integer proposedFloors,BigDecimal calculatedFar,String regulationStatus,
            int blockingReasonCount,int warningCount,String algorithmVersion,
            Map<String,Object> inputSnapshot,Map<String,Object> resultSnapshot,
            String assessedBy,Instant assessedAt,List<ReasonResponse> reasons
    ) {}
}