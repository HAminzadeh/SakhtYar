package com.sakhtyar.builder.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="builder_account_binding")
public class BuilderAccountBindingEntity {
    @Id @Column(name="builder_id") private UUID builderId;
    @Column(nullable=false,length=100) private String username;
    @Column(name="review_reference",nullable=false,length=500) private String reviewReference;
    @Column(name="bound_by",nullable=false,length=100) private String boundBy;
    @Column(name="bound_at",nullable=false) private Instant boundAt;
    protected BuilderAccountBindingEntity() { }
    public BuilderAccountBindingEntity(UUID builderId,String username,String reviewReference,String boundBy,Instant boundAt){
        this.builderId=builderId;this.username=username;this.reviewReference=reviewReference;this.boundBy=boundBy;this.boundAt=boundAt;}
    public UUID getBuilderId(){return builderId;}public String getUsername(){return username;}
}
