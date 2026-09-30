package com.sakhtyar.analysis.feasibility.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="feasibility_reason")
public class FeasibilityReasonEntity {
    @Id private UUID id;
    @Column(name="assessment_id",nullable=false) private UUID assessmentId;
    @Enumerated(EnumType.STRING) @Column(name="reason_type",nullable=false,length=40) private FeasibilityReasonType reasonType;
    @Column(name="reason_code",nullable=false,length=120) private String reasonCode;
    @Column(nullable=false,length=2000) private String message;
    @Column(name="source_entity_type",length=120) private String sourceEntityType;
    @Column(name="source_entity_id") private UUID sourceEntityId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private Map<String,Object> details;
    @Column(name="created_at",nullable=false) private Instant createdAt;

    protected FeasibilityReasonEntity(){}

    public FeasibilityReasonEntity(UUID id,UUID assessmentId,FeasibilityReasonType reasonType,
            String reasonCode,String message,String sourceEntityType,UUID sourceEntityId,
            Map<String,Object> details,Instant createdAt) {
        this.id=id; this.assessmentId=assessmentId; this.reasonType=reasonType;
        this.reasonCode=reasonCode; this.message=message; this.sourceEntityType=sourceEntityType;
        this.sourceEntityId=sourceEntityId; this.details=details==null?Map.of():Map.copyOf(details);
        this.createdAt=createdAt;
    }

    public UUID getId(){return id;} public UUID getAssessmentId(){return assessmentId;}
    public FeasibilityReasonType getReasonType(){return reasonType;} public String getReasonCode(){return reasonCode;}
    public String getMessage(){return message;} public String getSourceEntityType(){return sourceEntityType;}
    public UUID getSourceEntityId(){return sourceEntityId;} public Map<String,Object> getDetails(){return details==null?Map.of():Map.copyOf(details);}
    public Instant getCreatedAt(){return createdAt;}
}