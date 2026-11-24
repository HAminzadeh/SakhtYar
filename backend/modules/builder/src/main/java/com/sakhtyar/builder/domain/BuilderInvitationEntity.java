package com.sakhtyar.builder.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="builder_invitation")
public class BuilderInvitationEntity {
    @Id private UUID id;
    @Column(name="case_id",nullable=false) private UUID caseId;
    @Column(name="builder_id",nullable=false) private UUID builderId;
    @Column(nullable=false,length=24) private String status;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(name="created_by",nullable=false,length=100) private String createdBy;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected BuilderInvitationEntity() { }
    public BuilderInvitationEntity(UUID id,UUID caseId,UUID builderId,String status,Instant expiresAt,String createdBy,Instant now){
        this.id=id;this.caseId=caseId;this.builderId=builderId;this.status=status;this.expiresAt=expiresAt;
        this.createdBy=createdBy;this.createdAt=now;this.updatedAt=now;}
    public void status(String value){status=value;updatedAt=Instant.now();}
    public UUID getId(){return id;}public UUID getCaseId(){return caseId;}public UUID getBuilderId(){return builderId;}
    public String getStatus(){return status;}public Instant getExpiresAt(){return expiresAt;}
    public Instant getCreatedAt(){return createdAt;}public Instant getUpdatedAt(){return updatedAt;}
}
