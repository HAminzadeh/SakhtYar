package com.sakhtyar.builder.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="project_builder")
public class ProjectBuilderEntity {
    @Id private UUID id;
    @Column(name="case_id",nullable=false) private UUID caseId;
    @Column(name="builder_id",nullable=false) private UUID builderId;
    @Column(nullable=false,length=50) private String role;
    @Column(nullable=false,length=40) private String status;
    @Column(name="is_primary",nullable=false) private boolean primary;
    @Column(name="proposed_share_percent",precision=7,scale=4) private BigDecimal proposedSharePercent;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private Map<String,Object> metadata;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;

    protected ProjectBuilderEntity(){}

    public ProjectBuilderEntity(UUID id,UUID caseId,UUID builderId,String role,String status,boolean primary,
      BigDecimal proposedSharePercent,Map<String,Object> metadata,Instant createdAt,Instant updatedAt){
      this.id=id;this.caseId=caseId;this.builderId=builderId;this.role=role;this.status=status;this.primary=primary;
      this.proposedSharePercent=proposedSharePercent;this.metadata=mutable(metadata);this.createdAt=createdAt;this.updatedAt=updatedAt;
    }

    public void update(String role,String status,boolean primary,BigDecimal proposedSharePercent,Map<String,Object> metadata){
      this.role=role;this.status=status;this.primary=primary;this.proposedSharePercent=proposedSharePercent;
      this.metadata=mutable(metadata);this.updatedAt=Instant.now();
    }

    private Map<String,Object> mutable(Map<String,Object> source){return source==null?new LinkedHashMap<>():new LinkedHashMap<>(source);}
    public UUID getId(){return id;} public UUID getCaseId(){return caseId;} public UUID getBuilderId(){return builderId;}
    public String getRole(){return role;} public String getStatus(){return status;} public boolean isPrimary(){return primary;}
    public BigDecimal getProposedSharePercent(){return proposedSharePercent;}
    public Map<String,Object> getMetadata(){return metadata==null?Map.of():Map.copyOf(metadata);}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
