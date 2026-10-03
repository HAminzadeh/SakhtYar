package com.sakhtyar.analysis.feasibility.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="feasibility_assessment")
public class FeasibilityAssessmentEntity {
    @Id private UUID id;
    @Column(name="case_id",nullable=false) private UUID caseId;
    @Column(name="property_id",nullable=false) private UUID propertyId;
    @Column(name="scenario_id",nullable=false) private UUID scenarioId;
    @Column(name="scenario_cost_snapshot_id",nullable=false) private UUID scenarioCostSnapshotId;
    @Column(name="urban_evaluation_id",nullable=false) private UUID urbanEvaluationId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private FeasibilityStatus status;
    @Column(name="readiness_score",nullable=false,precision=5,scale=2) private BigDecimal readinessScore;
    @Column(name="scenario_cost",nullable=false,precision=20,scale=2) private BigDecimal scenarioCost;
    @Column(name="cost_currency_code",nullable=false,length=8) private String costCurrencyCode;
    @Column(name="currency_id", insertable=false, updatable=false)
    private UUID currencyId;
    @Column(name="land_area_m2",precision=20,scale=6) private BigDecimal landAreaM2;
    @Column(name="total_built_area_m2",precision=20,scale=6) private BigDecimal totalBuiltAreaM2;
    @Column(name="cost_per_land_m2",precision=20,scale=2) private BigDecimal costPerLandM2;
    @Column(name="cost_per_built_m2",precision=20,scale=2) private BigDecimal costPerBuiltM2;
    @Column(name="proposed_floors") private Integer proposedFloors;
    @Column(name="calculated_far",precision=20,scale=6) private BigDecimal calculatedFar;
    @Column(name="regulation_status",nullable=false,length=40) private String regulationStatus;
    @Column(name="blocking_reason_count",nullable=false) private int blockingReasonCount;
    @Column(name="warning_count",nullable=false) private int warningCount;
    @Column(name="algorithm_version",nullable=false,length=40) private String algorithmVersion;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="input_snapshot",nullable=false,columnDefinition="jsonb")
    private Map<String,Object> inputSnapshot;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="result_snapshot",nullable=false,columnDefinition="jsonb")
    private Map<String,Object> resultSnapshot;
    @Column(name="assessed_by",nullable=false,length=150) private String assessedBy;
    @Column(name="assessed_at",nullable=false) private Instant assessedAt;

    protected FeasibilityAssessmentEntity(){}

    public FeasibilityAssessmentEntity(UUID id,UUID caseId,UUID propertyId,UUID scenarioId,
            UUID scenarioCostSnapshotId,UUID urbanEvaluationId,FeasibilityStatus status,
            BigDecimal readinessScore,BigDecimal scenarioCost,String costCurrencyCode,
            BigDecimal landAreaM2,BigDecimal totalBuiltAreaM2,BigDecimal costPerLandM2,
            BigDecimal costPerBuiltM2,Integer proposedFloors,BigDecimal calculatedFar,
            String regulationStatus,int blockingReasonCount,int warningCount,String algorithmVersion,
            Map<String,Object> inputSnapshot,Map<String,Object> resultSnapshot,
            String assessedBy,Instant assessedAt) {
        this.id=id; this.caseId=caseId; this.propertyId=propertyId; this.scenarioId=scenarioId;
        this.scenarioCostSnapshotId=scenarioCostSnapshotId; this.urbanEvaluationId=urbanEvaluationId;
        this.status=status; this.readinessScore=readinessScore; this.scenarioCost=scenarioCost;
        this.costCurrencyCode=costCurrencyCode; this.landAreaM2=landAreaM2;
        this.totalBuiltAreaM2=totalBuiltAreaM2; this.costPerLandM2=costPerLandM2;
        this.costPerBuiltM2=costPerBuiltM2; this.proposedFloors=proposedFloors;
        this.calculatedFar=calculatedFar; this.regulationStatus=regulationStatus;
        this.blockingReasonCount=blockingReasonCount; this.warningCount=warningCount;
        this.algorithmVersion=algorithmVersion;
        this.inputSnapshot=inputSnapshot==null?Map.of():Map.copyOf(inputSnapshot);
        this.resultSnapshot=resultSnapshot==null?Map.of():Map.copyOf(resultSnapshot);
        this.assessedBy=assessedBy; this.assessedAt=assessedAt;
    }

    public UUID getId(){return id;} public UUID getCaseId(){return caseId;}
    public UUID getPropertyId(){return propertyId;} public UUID getScenarioId(){return scenarioId;}
    public UUID getScenarioCostSnapshotId(){return scenarioCostSnapshotId;}
    public UUID getUrbanEvaluationId(){return urbanEvaluationId;} public FeasibilityStatus getStatus(){return status;}
    public BigDecimal getReadinessScore(){return readinessScore;} public BigDecimal getScenarioCost(){return scenarioCost;}
    public String getCostCurrencyCode(){return costCurrencyCode;} public BigDecimal getLandAreaM2(){return landAreaM2;}
    public BigDecimal getTotalBuiltAreaM2(){return totalBuiltAreaM2;} public BigDecimal getCostPerLandM2(){return costPerLandM2;}
    public BigDecimal getCostPerBuiltM2(){return costPerBuiltM2;} public Integer getProposedFloors(){return proposedFloors;}
    public BigDecimal getCalculatedFar(){return calculatedFar;} public String getRegulationStatus(){return regulationStatus;}
    public int getBlockingReasonCount(){return blockingReasonCount;} public int getWarningCount(){return warningCount;}
    public String getAlgorithmVersion(){return algorithmVersion;}
    public Map<String,Object> getInputSnapshot(){return inputSnapshot==null?Map.of():Map.copyOf(inputSnapshot);}
    public Map<String,Object> getResultSnapshot(){return resultSnapshot==null?Map.of():Map.copyOf(resultSnapshot);}
    public String getAssessedBy(){return assessedBy;} public Instant getAssessedAt(){return assessedAt;}

    public UUID getCurrencyId(){return currencyId;}
}