package com.sakhtyar.regulation.api;

import com.sakhtyar.regulation.domain.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class RegulationDtos {
    private RegulationDtos(){}

    public record UpsertRuleRequest(
            @NotBlank @Size(max=140) String code,
            @NotBlank @Size(max=500) String nameFa,
            @Size(max=500) String nameEn,
            @NotNull UrbanRuleType ruleType,
            @Size(max=2) String jurisdictionCountry,
            @Size(max=100) String jurisdictionProvince,
            @Size(max=100) String jurisdictionCity,
            @Size(max=100) String jurisdictionDistrict,
            @Size(max=80) String propertyType,
            @NotNull UUID sourceId,
            @Size(max=2000) String sourceUrl,
            LocalDate validFrom,
            LocalDate validTo,
            @Min(0) int priority,
            boolean active,
            @NotNull Map<String,Object> parameters
    ) {}

    public record EvaluateRequest(@NotNull UUID propertyId, UUID scenarioId) {}

    public record RuleResponse(
            UUID id,String code,String nameFa,String nameEn,UrbanRuleType ruleType,
            String jurisdictionCountry,String jurisdictionProvince,String jurisdictionCity,
            String jurisdictionDistrict,String propertyType,UUID sourceId,String sourceUrl,
            LocalDate validFrom,LocalDate validTo,int priority,boolean active,
            Map<String,Object> parameters,String createdBy,Instant createdAt,Instant updatedAt
    ) {
        public static RuleResponse from(UrbanRuleEntity e) {
            return new RuleResponse(e.getId(),e.getCode(),e.getNameFa(),e.getNameEn(),e.getRuleType(),
                e.getJurisdictionCountry(),e.getJurisdictionProvince(),e.getJurisdictionCity(),
                e.getJurisdictionDistrict(),e.getPropertyType(),e.getSourceId(),e.getSourceUrl(),
                e.getValidFrom(),e.getValidTo(),e.getPriority(),e.isActive(),e.getParameters(),
                e.getCreatedBy(),e.getCreatedAt(),e.getUpdatedAt());
        }
    }

    public record ResultResponse(
            UUID id,UUID ruleId,EvaluationOutcome outcome,String actualValue,String expectedValue,
            String message,Map<String,Object> details,Instant createdAt
    ) {
        public static ResultResponse from(UrbanEvaluationResultEntity e) {
            return new ResultResponse(e.getId(),e.getRuleId(),e.getOutcome(),e.getActualValue(),
                e.getExpectedValue(),e.getMessage(),e.getDetails(),e.getCreatedAt());
        }
    }

    public record EvaluationResponse(
            UUID id,UUID propertyId,UUID scenarioId,EvaluationStatus status,int ruleCount,
            int passedCount,int failedCount,int reviewCount,String algorithmVersion,
            Map<String,Object> inputSnapshot,String evaluatedBy,Instant evaluatedAt,
            List<ResultResponse> results
    ) {}
}