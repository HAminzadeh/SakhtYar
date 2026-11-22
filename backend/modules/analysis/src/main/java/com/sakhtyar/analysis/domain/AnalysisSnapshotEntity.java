package com.sakhtyar.analysis.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "analysis_snapshot")
public class AnalysisSnapshotEntity {

    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "scenario_id")
    private UUID scenarioId;

    @Column(name = "analysis_type", nullable = false, length = 80)
    private String analysisType;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "schema_version", nullable = false, length = 20)
    private String schemaVersion;

    @Column(name = "calculation_engine_version", length = 50)
    private String calculationEngineVersion;

    @Column(name = "knowledge_version", length = 50)
    private String knowledgeVersion;

    @Column(name = "regulation_version", length = 50)
    private String regulationVersion;

    @Column(name = "material_price_version", length = 50)
    private String materialPriceVersion;

    @Column(name = "root_entity_type", length = 100)
    private String rootEntityType;

    @Column(name = "root_entity_id")
    private UUID rootEntityId;

    @Column(name = "parent_snapshot_id")
    private UUID parentSnapshotId;

    @Column(name = "content_sha256", length = 64)
    private String contentSha256;

    @Column(name = "source_count", nullable = false)
    private int sourceCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String,Object> inputSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String,Object> resultSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String,Object> metadata;

    @Column(name = "created_by", nullable = false, length = 150)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AnalysisSnapshotEntity() {}

    public AnalysisSnapshotEntity(UUID id,UUID caseId,UUID scenarioId,String analysisType,String status,
            String schemaVersion,String calculationEngineVersion,String knowledgeVersion,
            String regulationVersion,String materialPriceVersion,String rootEntityType,UUID rootEntityId,
            UUID parentSnapshotId,String contentSha256,int sourceCount,Map<String,Object> inputSnapshot,
            Map<String,Object> resultSnapshot,Map<String,Object> metadata,String createdBy,Instant createdAt) {
        this.id=id; this.caseId=caseId; this.scenarioId=scenarioId; this.analysisType=analysisType;
        this.status=status; this.schemaVersion=schemaVersion; this.calculationEngineVersion=calculationEngineVersion;
        this.knowledgeVersion=knowledgeVersion; this.regulationVersion=regulationVersion;
        this.materialPriceVersion=materialPriceVersion; this.rootEntityType=rootEntityType;
        this.rootEntityId=rootEntityId; this.parentSnapshotId=parentSnapshotId;
        this.contentSha256=contentSha256; this.sourceCount=sourceCount;
        this.inputSnapshot=inputSnapshot==null?Map.of():Map.copyOf(inputSnapshot);
        this.resultSnapshot=resultSnapshot==null?Map.of():Map.copyOf(resultSnapshot);
        this.metadata=metadata==null?Map.of():Map.copyOf(metadata);
        this.createdBy=createdBy; this.createdAt=createdAt;
    }

    public UUID getId(){return id;} public UUID getCaseId(){return caseId;}
    public UUID getScenarioId(){return scenarioId;} public String getAnalysisType(){return analysisType;}
    public String getStatus(){return status;} public String getSchemaVersion(){return schemaVersion;}
    public String getCalculationEngineVersion(){return calculationEngineVersion;}
    public String getKnowledgeVersion(){return knowledgeVersion;} public String getRegulationVersion(){return regulationVersion;}
    public String getMaterialPriceVersion(){return materialPriceVersion;} public String getRootEntityType(){return rootEntityType;}
    public UUID getRootEntityId(){return rootEntityId;} public UUID getParentSnapshotId(){return parentSnapshotId;}
    public String getContentSha256(){return contentSha256;} public int getSourceCount(){return sourceCount;}
    public Map<String,Object> getInputSnapshot(){return inputSnapshot==null?Map.of():Map.copyOf(inputSnapshot);}
    public Map<String,Object> getResultSnapshot(){return resultSnapshot==null?Map.of():Map.copyOf(resultSnapshot);}
    public Map<String,Object> getMetadata(){return metadata==null?Map.of():Map.copyOf(metadata);}
    public String getCreatedBy(){return createdBy;} public Instant getCreatedAt(){return createdAt;}
}