package com.sakhtyar.analysis.api;

import com.sakhtyar.analysis.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class AnalysisSnapshotDtos {
    private AnalysisSnapshotDtos(){}

    public record LineageRequest(
            @NotBlank @Size(max=500) String outputPath,
            @NotBlank @Size(max=50) String relationshipType,
            @NotBlank @Size(max=50) String sourceType,
            @Size(max=100) String sourceEntityType,
            UUID sourceEntityId,
            @Size(max=3000) String sourceUrl,
            @Size(max=500) String sourceLabel,
            Instant sourceObservedAt,
            @Size(max=100) String sourceVersion,
            @Size(max=128) String sourceHash,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
            Map<String,Object> metadata
    ) {}

    public record CreateSnapshotRequest(
            @NotNull UUID caseId,
            UUID scenarioId,
            @NotBlank @Size(max=80) String analysisType,
            @NotBlank @Size(max=40) String status,
            @NotBlank @Size(max=20) String schemaVersion,
            @Size(max=50) String calculationEngineVersion,
            @Size(max=50) String knowledgeVersion,
            @Size(max=50) String regulationVersion,
            @Size(max=50) String materialPriceVersion,
            @Size(max=100) String rootEntityType,
            UUID rootEntityId,
            UUID parentSnapshotId,
            @NotNull Map<String,Object> inputSnapshot,
            @NotNull Map<String,Object> resultSnapshot,
            Map<String,Object> metadata,
            @NotEmpty List<@Valid LineageRequest> lineage
    ) {}

    public record LineageResponse(
            UUID id,String outputPath,String relationshipType,String sourceType,
            String sourceEntityType,UUID sourceEntityId,String sourceUrl,String sourceLabel,
            Instant sourceObservedAt,String sourceVersion,String sourceHash,BigDecimal confidence,
            Map<String,Object> metadata,Instant createdAt
    ) {
        public static LineageResponse from(DataLineageEntity e) {
            return new LineageResponse(e.getId(),e.getOutputPath(),e.getRelationshipType(),
                    e.getSourceType(),e.getSourceEntityType(),e.getSourceEntityId(),e.getSourceUrl(),
                    e.getSourceLabel(),e.getSourceObservedAt(),e.getSourceVersion(),e.getSourceHash(),
                    e.getConfidence(),e.getMetadata(),e.getCreatedAt());
        }
    }

    public record SnapshotResponse(
            UUID id,UUID caseId,UUID scenarioId,String analysisType,String status,String schemaVersion,
            String calculationEngineVersion,String knowledgeVersion,String regulationVersion,
            String materialPriceVersion,String rootEntityType,UUID rootEntityId,UUID parentSnapshotId,
            String contentSha256,int sourceCount,Map<String,Object> inputSnapshot,
            Map<String,Object> resultSnapshot,Map<String,Object> metadata,String createdBy,
            Instant createdAt,List<LineageResponse> lineage
    ) {}
}