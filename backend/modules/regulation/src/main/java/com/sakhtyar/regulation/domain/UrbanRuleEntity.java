package com.sakhtyar.regulation.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "urban_rule")
public class UrbanRuleEntity {
    @Id private UUID id;
    @Column(nullable=false, unique=true, length=140) private String code;
    @Column(name="name_fa", nullable=false, length=500) private String nameFa;
    @Column(name="name_en", length=500) private String nameEn;
    @Enumerated(EnumType.STRING)
    @Column(name="rule_type", nullable=false, length=50) private UrbanRuleType ruleType;
    @Column(name="jurisdiction_country", length=2) private String jurisdictionCountry;
    @Column(name="jurisdiction_province", length=100) private String jurisdictionProvince;
    @Column(name="jurisdiction_city", length=100) private String jurisdictionCity;
    @Column(name="jurisdiction_district", length=100) private String jurisdictionDistrict;
    @Column(name="property_type", length=80) private String propertyType;
    @Column(name="source_id", nullable=false) private UUID sourceId;
    @Column(name="source_url", length=2000) private String sourceUrl;
    @Column(name="valid_from") private LocalDate validFrom;
    @Column(name="valid_to") private LocalDate validTo;
    @Column(nullable=false) private int priority;
    @Column(nullable=false) private boolean active;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable=false, columnDefinition="jsonb") private Map<String,Object> parameters;
    @Column(name="created_by", nullable=false, length=150) private String createdBy;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;

    protected UrbanRuleEntity(){}

    public UrbanRuleEntity(UUID id,String code,String nameFa,String nameEn,UrbanRuleType ruleType,
            String country,String province,String city,String district,String propertyType,
            UUID sourceId,String sourceUrl,LocalDate validFrom,LocalDate validTo,int priority,
            boolean active,Map<String,Object> parameters,String createdBy,Instant now) {
        this.id=id; this.code=code; this.nameFa=nameFa; this.nameEn=nameEn; this.ruleType=ruleType;
        this.jurisdictionCountry=country; this.jurisdictionProvince=province; this.jurisdictionCity=city;
        this.jurisdictionDistrict=district; this.propertyType=propertyType; this.sourceId=sourceId;
        this.sourceUrl=sourceUrl; this.validFrom=validFrom; this.validTo=validTo; this.priority=priority;
        this.active=active; this.parameters=parameters==null?Map.of():Map.copyOf(parameters);
        this.createdBy=createdBy; this.createdAt=now; this.updatedAt=now;
    }

    public void update(String nameFa,String nameEn,UrbanRuleType ruleType,String country,
            String province,String city,String district,String propertyType,UUID sourceId,
            String sourceUrl,LocalDate validFrom,LocalDate validTo,int priority,boolean active,
            Map<String,Object> parameters,Instant now) {
        this.nameFa=nameFa; this.nameEn=nameEn; this.ruleType=ruleType; this.jurisdictionCountry=country;
        this.jurisdictionProvince=province; this.jurisdictionCity=city; this.jurisdictionDistrict=district;
        this.propertyType=propertyType; this.sourceId=sourceId; this.sourceUrl=sourceUrl;
        this.validFrom=validFrom; this.validTo=validTo; this.priority=priority; this.active=active;
        this.parameters=parameters==null?Map.of():Map.copyOf(parameters); this.updatedAt=now;
    }

    public UUID getId(){return id;} public String getCode(){return code;}
    public String getNameFa(){return nameFa;} public String getNameEn(){return nameEn;}
    public UrbanRuleType getRuleType(){return ruleType;} public String getJurisdictionCountry(){return jurisdictionCountry;}
    public String getJurisdictionProvince(){return jurisdictionProvince;} public String getJurisdictionCity(){return jurisdictionCity;}
    public String getJurisdictionDistrict(){return jurisdictionDistrict;} public String getPropertyType(){return propertyType;}
    public UUID getSourceId(){return sourceId;} public String getSourceUrl(){return sourceUrl;}
    public LocalDate getValidFrom(){return validFrom;} public LocalDate getValidTo(){return validTo;}
    public int getPriority(){return priority;} public boolean isActive(){return active;}
    public Map<String,Object> getParameters(){return parameters==null?Map.of():Map.copyOf(parameters);}
    public String getCreatedBy(){return createdBy;} public Instant getCreatedAt(){return createdAt;}
    public Instant getUpdatedAt(){return updatedAt;}
}