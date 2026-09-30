package com.sakhtyar.regulation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="urban_evaluation")
public class UrbanEvaluationEntity {
    @Id private UUID id;
    @Column(name="property_id",nullable=false) private UUID propertyId;
    @Column(name="scenario_id") private UUID scenarioId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private EvaluationStatus status;
    @Column(name="rule_count",nullable=false) private int ruleCount;
    @Column(name="passed_count",nullable=false) private int passedCount;
    @Column(name="failed_count",nullable=false) private int failedCount;
    @Column(name="review_count",nullable=false) private int reviewCount;
    @Column(name="algorithm_version",nullable=false,length=40) private String algorithmVersion;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="input_snapshot",nullable=false,columnDefinition="jsonb")
    private Map<String,Object> inputSnapshot;
    @Column(name="evaluated_by",nullable=false,length=150) private String evaluatedBy;
    @Column(name="evaluated_at",nullable=false) private Instant evaluatedAt;

    protected UrbanEvaluationEntity(){}

    public UrbanEvaluationEntity(UUID id,UUID propertyId,UUID scenarioId,EvaluationStatus status,
            int ruleCount,int passedCount,int failedCount,int reviewCount,String algorithmVersion,
            Map<String,Object> inputSnapshot,String evaluatedBy,Instant evaluatedAt) {
        this.id=id; this.propertyId=propertyId; this.scenarioId=scenarioId; this.status=status;
        this.ruleCount=ruleCount; this.passedCount=passedCount; this.failedCount=failedCount;
        this.reviewCount=reviewCount; this.algorithmVersion=algorithmVersion;
        this.inputSnapshot=inputSnapshot==null?Map.of():Map.copyOf(inputSnapshot);
        this.evaluatedBy=evaluatedBy; this.evaluatedAt=evaluatedAt;
    }

    public UUID getId(){return id;} public UUID getPropertyId(){return propertyId;}
    public UUID getScenarioId(){return scenarioId;} public EvaluationStatus getStatus(){return status;}
    public int getRuleCount(){return ruleCount;} public int getPassedCount(){return passedCount;}
    public int getFailedCount(){return failedCount;} public int getReviewCount(){return reviewCount;}
    public String getAlgorithmVersion(){return algorithmVersion;}
    public Map<String,Object> getInputSnapshot(){return inputSnapshot==null?Map.of():Map.copyOf(inputSnapshot);}
    public String getEvaluatedBy(){return evaluatedBy;} public Instant getEvaluatedAt(){return evaluatedAt;}
}