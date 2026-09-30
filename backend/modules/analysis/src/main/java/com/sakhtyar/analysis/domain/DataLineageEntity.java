package com.sakhtyar.analysis.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "data_lineage")
public class DataLineageEntity {

    @Id
    private UUID id;

    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "analysis_snapshot_id")
    private UUID analysisSnapshotId;

    @Column(name = "output_path", nullable = false, length = 500)
    private String outputPath;

    @Column(name = "relationship_type", nullable = false, length = 50)
    private String relationshipType;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    @Column(name = "source_entity_type", length = 100)
    private String sourceEntityType;

    @Column(name = "source_entity_id")
    private UUID sourceEntityId;

    @Column(name = "source_url", length = 3000)
    private String sourceUrl;

    @Column(name = "source_label", length = 500)
    private String sourceLabel;

    @Column(name = "source_observed_at")
    private Instant sourceObservedAt;

    @Column(name = "source_version", length = 100)
    private String sourceVersion;

    @Column(name = "source_hash", length = 128)
    private String sourceHash;

    @Column(precision = 6, scale = 5)
    private BigDecimal confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String,Object> metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DataLineageEntity() {}

    public DataLineageEntity(UUID id,UUID caseId,UUID analysisSnapshotId,String outputPath,
            String relationshipType,String sourceType,String sourceEntityType,UUID sourceEntityId,
            String sourceUrl,String sourceLabel,Instant sourceObservedAt,String sourceVersion,
            String sourceHash,BigDecimal confidence,Map<String,Object> metadata,Instant createdAt) {
        this.id=id; this.caseId=caseId; this.analysisSnapshotId=analysisSnapshotId;
        this.outputPath=outputPath; this.relationshipType=relationshipType; this.sourceType=sourceType;
        this.sourceEntityType=sourceEntityType; this.sourceEntityId=sourceEntityId; this.sourceUrl=sourceUrl;
        this.sourceLabel=sourceLabel; this.sourceObservedAt=sourceObservedAt; this.sourceVersion=sourceVersion;
        this.sourceHash=sourceHash; this.confidence=confidence;
        this.metadata=metadata==null?Map.of():Map.copyOf(metadata); this.createdAt=createdAt;
    }

    public UUID getId(){return id;} public UUID getCaseId(){return caseId;}
    public UUID getAnalysisSnapshotId(){return analysisSnapshotId;} public String getOutputPath(){return outputPath;}
    public String getRelationshipType(){return relationshipType;} public String getSourceType(){return sourceType;}
    public String getSourceEntityType(){return sourceEntityType;} public UUID getSourceEntityId(){return sourceEntityId;}
    public String getSourceUrl(){return sourceUrl;} public String getSourceLabel(){return sourceLabel;}
    public Instant getSourceObservedAt(){return sourceObservedAt;} public String getSourceVersion(){return sourceVersion;}
    public String getSourceHash(){return sourceHash;} public BigDecimal getConfidence(){return confidence;}
    public Map<String,Object> getMetadata(){return metadata==null?Map.of():Map.copyOf(metadata);}
    public Instant getCreatedAt(){return createdAt;}
}