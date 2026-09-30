package com.sakhtyar.finance.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="financial_participation_allocation")
public class FinancialParticipationAllocationEntity {
    @Id private UUID id;
    @Column(name="financial_analysis_id",nullable=false) private UUID financialAnalysisId;
    @Enumerated(EnumType.STRING) @Column(name="participant_type",nullable=false,length=40) private ParticipantType participantType;
    @Column(name="participant_ref_id") private UUID participantRefId;
    @Column(name="participant_label",nullable=false,length=400) private String participantLabel;
    @Column(name="value_share_percent",nullable=false,precision=9,scale=6) private BigDecimal valueSharePercent;
    @Column(name="cost_share_percent",nullable=false,precision=9,scale=6) private BigDecimal costSharePercent;
    @Column(name="allocated_revenue",nullable=false,precision=20,scale=2) private BigDecimal allocatedRevenue;
    @Column(name="allocated_cost",nullable=false,precision=20,scale=2) private BigDecimal allocatedCost;
    @Column(name="projected_net_value",nullable=false,precision=20,scale=2) private BigDecimal projectedNetValue;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private Map<String,Object> metadata;
    @Column(name="created_at",nullable=false) private Instant createdAt;

    protected FinancialParticipationAllocationEntity(){}

    public FinancialParticipationAllocationEntity(UUID id,UUID financialAnalysisId,ParticipantType participantType,
            UUID participantRefId,String participantLabel,BigDecimal valueSharePercent,BigDecimal costSharePercent,
            BigDecimal allocatedRevenue,BigDecimal allocatedCost,BigDecimal projectedNetValue,
            Map<String,Object> metadata,Instant createdAt) {
        this.id=id; this.financialAnalysisId=financialAnalysisId; this.participantType=participantType;
        this.participantRefId=participantRefId; this.participantLabel=participantLabel;
        this.valueSharePercent=valueSharePercent; this.costSharePercent=costSharePercent;
        this.allocatedRevenue=allocatedRevenue; this.allocatedCost=allocatedCost;
        this.projectedNetValue=projectedNetValue; this.metadata=metadata==null?Map.of():Map.copyOf(metadata);
        this.createdAt=createdAt;
    }

    public UUID getId(){return id;} public UUID getFinancialAnalysisId(){return financialAnalysisId;}
    public ParticipantType getParticipantType(){return participantType;} public UUID getParticipantRefId(){return participantRefId;}
    public String getParticipantLabel(){return participantLabel;} public BigDecimal getValueSharePercent(){return valueSharePercent;}
    public BigDecimal getCostSharePercent(){return costSharePercent;} public BigDecimal getAllocatedRevenue(){return allocatedRevenue;}
    public BigDecimal getAllocatedCost(){return allocatedCost;} public BigDecimal getProjectedNetValue(){return projectedNetValue;}
    public Map<String,Object> getMetadata(){return metadata==null?Map.of():Map.copyOf(metadata);}
    public Instant getCreatedAt(){return createdAt;}
}