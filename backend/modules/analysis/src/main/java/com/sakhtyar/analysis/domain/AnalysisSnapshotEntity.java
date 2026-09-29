package com.sakhtyar.analysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "analysis_snapshot")
public class AnalysisSnapshotEntity {

    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> inputSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> resultSnapshot;

    @Column(name = "created_by", nullable = false, length = 150)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AnalysisSnapshotEntity() {}

    public UUID getId() { return id; }
    public UUID getCaseId() { return caseId; }
    public String getAnalysisType() { return analysisType; }
    public String getStatus() { return status; }
    public String getSchemaVersion() { return schemaVersion; }
    public String getCalculationEngineVersion() { return calculationEngineVersion; }
    public String getKnowledgeVersion() { return knowledgeVersion; }
    public String getRegulationVersion() { return regulationVersion; }
    public String getMaterialPriceVersion() { return materialPriceVersion; }
    public Map<String, Object> getInputSnapshot() { return inputSnapshot == null ? Map.of() : Map.copyOf(inputSnapshot); }
    public Map<String, Object> getResultSnapshot() { return resultSnapshot == null ? Map.of() : Map.copyOf(resultSnapshot); }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}