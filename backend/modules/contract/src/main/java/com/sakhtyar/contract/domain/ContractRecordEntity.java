package com.sakhtyar.contract.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="contract_record")
public class ContractRecordEntity {
    @Id private UUID id;
    @Column(name="case_id",nullable=false) private UUID caseId;
    @Column(name="contract_code",nullable=false,length=80) private String contractCode;
    @Column(name="contract_type",nullable=false,length=60) private String contractType;
    @Column(nullable=false,length=40) private String status;
    @Column(nullable=false,length=300) private String title;
    @Column(name="signed_at") private Instant signedAt;
    @Column(name="effective_from") private LocalDate effectiveFrom;
    @Column(name="effective_to") private LocalDate effectiveTo;
    @Column(name="schema_version",nullable=false,length=20) private String schemaVersion;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private Map<String,Object> terms;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private Map<String,Object> metadata;
    @Column(name="created_by",nullable=false,length=150) private String createdBy;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;

    protected ContractRecordEntity(){}

    public ContractRecordEntity(UUID id,UUID caseId,String contractCode,String contractType,String status,String title,
      Instant signedAt,LocalDate effectiveFrom,LocalDate effectiveTo,String schemaVersion,Map<String,Object> terms,
      Map<String,Object> metadata,String createdBy,Instant createdAt,Instant updatedAt){
      this.id=id;this.caseId=caseId;this.contractCode=contractCode;this.contractType=contractType;this.status=status;this.title=title;
      this.signedAt=signedAt;this.effectiveFrom=effectiveFrom;this.effectiveTo=effectiveTo;this.schemaVersion=schemaVersion;
      this.terms=mutable(terms);this.metadata=mutable(metadata);this.createdBy=createdBy;this.createdAt=createdAt;this.updatedAt=updatedAt;
    }

    public void update(String contractType,String status,String title,Instant signedAt,LocalDate effectiveFrom,LocalDate effectiveTo,
      String schemaVersion,Map<String,Object> terms,Map<String,Object> metadata){
      this.contractType=contractType;this.status=status;this.title=title;this.signedAt=signedAt;this.effectiveFrom=effectiveFrom;
      this.effectiveTo=effectiveTo;this.schemaVersion=schemaVersion;this.terms=mutable(terms);this.metadata=mutable(metadata);
      this.updatedAt=Instant.now();
    }

    private Map<String,Object> mutable(Map<String,Object> v){return v==null?new LinkedHashMap<>():new LinkedHashMap<>(v);}
    public UUID getId(){return id;} public UUID getCaseId(){return caseId;} public String getContractCode(){return contractCode;}
    public String getContractType(){return contractType;} public String getStatus(){return status;} public String getTitle(){return title;}
    public Instant getSignedAt(){return signedAt;} public LocalDate getEffectiveFrom(){return effectiveFrom;}
    public LocalDate getEffectiveTo(){return effectiveTo;} public String getSchemaVersion(){return schemaVersion;}
    public Map<String,Object> getTerms(){return terms==null?Map.of():Map.copyOf(terms);}
    public Map<String,Object> getMetadata(){return metadata==null?Map.of():Map.copyOf(metadata);}
    public String getCreatedBy(){return createdBy;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
