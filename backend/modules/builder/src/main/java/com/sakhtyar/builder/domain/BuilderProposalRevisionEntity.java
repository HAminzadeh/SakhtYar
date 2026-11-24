package com.sakhtyar.builder.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="builder_proposal_revision")
public class BuilderProposalRevisionEntity {
    @Id private UUID id;
    @Column(name="proposal_id",nullable=false) private UUID proposalId;
    @Column(name="revision_no",nullable=false) private int revisionNo;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private Map<String,Object> snapshot;
    @Column(name="recorded_by",nullable=false,length=100) private String recordedBy;
    @Column(name="recorded_at",nullable=false) private Instant recordedAt;
    protected BuilderProposalRevisionEntity() { }
    public BuilderProposalRevisionEntity(UUID id,UUID proposalId,int revisionNo,Map<String,Object> snapshot,String recordedBy,Instant recordedAt){
        this.id=id;this.proposalId=proposalId;this.revisionNo=revisionNo;
        this.snapshot=new LinkedHashMap<>(snapshot);this.recordedBy=recordedBy;this.recordedAt=recordedAt;}
    public UUID getId(){return id;}public UUID getProposalId(){return proposalId;}
    public int getRevisionNo(){return revisionNo;}public Map<String,Object> getSnapshot(){return Collections.unmodifiableMap(snapshot);}
    public String getRecordedBy(){return recordedBy;}public Instant getRecordedAt(){return recordedAt;}
}
