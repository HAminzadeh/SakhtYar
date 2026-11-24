package com.sakhtyar.builder.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="builder_verification_event")
public class BuilderVerificationEntity {
    @Id private UUID id;
    @Column(name="builder_id",nullable=false) private UUID builderId;
    @Column(nullable=false,length=20) private String decision;
    @Column(name="evidence_reference",nullable=false,length=500) private String evidenceReference;
    @Column(name="reviewed_by",nullable=false,length=100) private String reviewedBy;
    @Column(name="reviewed_at",nullable=false) private Instant reviewedAt;
    protected BuilderVerificationEntity() { }
    public BuilderVerificationEntity(UUID id,UUID builderId,String decision,String evidenceReference,String reviewedBy,Instant reviewedAt){
        this.id=id;this.builderId=builderId;this.decision=decision;this.evidenceReference=evidenceReference;
        this.reviewedBy=reviewedBy;this.reviewedAt=reviewedAt;}
}
