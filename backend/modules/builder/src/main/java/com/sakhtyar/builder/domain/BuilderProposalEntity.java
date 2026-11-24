package com.sakhtyar.builder.domain;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="builder_proposal")
public class BuilderProposalEntity {
    @Id private UUID id;
    @Column(name="case_id",nullable=false) private UUID caseId;
    @Column(name="builder_id",nullable=false) private UUID builderId;
    @Column(name="invitation_id",nullable=false) private UUID invitationId;
    @Column(nullable=false,length=24) private String status;
    @Column(name="revision_no",nullable=false) private int revisionNo;
    @Column(name="proposed_share_percent",nullable=false,precision=7,scale=4) private BigDecimal proposedSharePercent;
    @Column(name="duration_months",nullable=false) private int durationMonths;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private Map<String,Object> terms;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected BuilderProposalEntity() { }
    public BuilderProposalEntity(UUID id,UUID caseId,UUID builderId,UUID invitationId,BigDecimal share,int duration,Map<String,Object> terms,Instant now){
        this.id=id;this.caseId=caseId;this.builderId=builderId;this.invitationId=invitationId;
        this.proposedSharePercent=share;this.durationMonths=duration;this.terms=new LinkedHashMap<>(terms==null?Map.of():terms);
        this.status="SUBMITTED";this.revisionNo=1;this.createdAt=now;this.updatedAt=now;}
    public void revise(BigDecimal share,int duration,Map<String,Object> terms){
        proposedSharePercent=share;durationMonths=duration;this.terms=new LinkedHashMap<>(terms==null?Map.of():terms);
        revisionNo++;status="SUBMITTED";updatedAt=Instant.now();}
    public void decide(String state){status=state;updatedAt=Instant.now();}
    public UUID getId(){return id;}public UUID getCaseId(){return caseId;}public UUID getBuilderId(){return builderId;}
    public UUID getInvitationId(){return invitationId;}public String getStatus(){return status;}
    public int getRevisionNo(){return revisionNo;}public BigDecimal getProposedSharePercent(){return proposedSharePercent;}
    public int getDurationMonths(){return durationMonths;}public Map<String,Object> getTerms(){return Collections.unmodifiableMap(terms==null?Map.of():terms);}
    public Instant getCreatedAt(){return createdAt;}public Instant getUpdatedAt(){return updatedAt;}
}
