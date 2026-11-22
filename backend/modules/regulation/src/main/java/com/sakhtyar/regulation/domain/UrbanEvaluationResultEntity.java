package com.sakhtyar.regulation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="urban_evaluation_result")
public class UrbanEvaluationResultEntity {
    @Id private UUID id;
    @Column(name="evaluation_id",nullable=false) private UUID evaluationId;
    @Column(name="rule_id",nullable=false) private UUID ruleId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private EvaluationOutcome outcome;
    @Column(name="actual_value",length=500) private String actualValue;
    @Column(name="expected_value",length=500) private String expectedValue;
    @Column(nullable=false,length=2000) private String message;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private Map<String,Object> details;
    @Column(name="created_at",nullable=false) private Instant createdAt;

    protected UrbanEvaluationResultEntity(){}

    public UrbanEvaluationResultEntity(UUID id,UUID evaluationId,UUID ruleId,EvaluationOutcome outcome,
            String actualValue,String expectedValue,String message,Map<String,Object> details,Instant createdAt) {
        this.id=id; this.evaluationId=evaluationId; this.ruleId=ruleId; this.outcome=outcome;
        this.actualValue=actualValue; this.expectedValue=expectedValue; this.message=message;
        this.details=details==null?Map.of():Map.copyOf(details); this.createdAt=createdAt;
    }

    public UUID getId(){return id;} public UUID getEvaluationId(){return evaluationId;}
    public UUID getRuleId(){return ruleId;} public EvaluationOutcome getOutcome(){return outcome;}
    public String getActualValue(){return actualValue;} public String getExpectedValue(){return expectedValue;}
    public String getMessage(){return message;} public Map<String,Object> getDetails(){return details==null?Map.of():Map.copyOf(details);}
    public Instant getCreatedAt(){return createdAt;}
}