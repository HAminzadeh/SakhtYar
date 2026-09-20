package com.sakhtyar.property.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "property")
public class PropertyEntity {

    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false, unique = true)
    private UUID caseId;

    @Column(length = 100)
    private String province;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String district;

    @Column(length = 150)
    private String neighborhood;

    @Column(columnDefinition = "text")
    private String address;

    @Column(name = "land_area_m2", precision = 12, scale = 2)
    private BigDecimal landAreaM2;

    @Column(name = "frontage_m", precision = 10, scale = 2)
    private BigDecimal frontageM;

    @Column(name = "passage_width_m", precision = 10, scale = 2)
    private BigDecimal passageWidthM;

    @Column(name = "building_area_m2", precision = 12, scale = 2)
    private BigDecimal buildingAreaM2;

    @Column(name = "construction_year")
    private Integer constructionYear;

    @Column(name = "existing_floors")
    private Integer existingFloors;

    @Column(name = "existing_units")
    private Integer existingUnits;

    @Column(length = 30)
    private String orientation;

    @Column(name = "property_type", length = 80)
    private String propertyType;

    @Column(name = "building_condition", length = 80)
    private String buildingCondition;

    @Column(name = "registry_main_no", length = 100)
    private String registryMainNo;

    @Column(name = "registry_sub_no", length = 100)
    private String registrySubNo;

    @Column(name = "registry_section", length = 100)
    private String registrySection;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(
            name = "attributes_schema_version",
            nullable = false,
            length = 20
    )
    private String attributesSchemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "attributes",
            nullable = false,
            columnDefinition = "jsonb"
    )
    private Map<String, Object> attributes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PropertyEntity() {
    }

    public PropertyEntity(
            UUID id,
            UUID caseId,
            String province,
            String city,
            String district,
            String neighborhood,
            String address,
            BigDecimal landAreaM2,
            BigDecimal frontageM,
            BigDecimal passageWidthM,
            BigDecimal buildingAreaM2,
            Integer constructionYear,
            Integer existingFloors,
            Integer existingUnits,
            String orientation,
            String propertyType,
            String buildingCondition,
            String registryMainNo,
            String registrySubNo,
            String registrySection,
            String postalCode,
            BigDecimal latitude,
            BigDecimal longitude,
            Map<String, Object> attributes,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.caseId = caseId;
        this.province = province;
        this.city = city;
        this.district = district;
        this.neighborhood = neighborhood;
        this.address = address;
        this.landAreaM2 = landAreaM2;
        this.frontageM = frontageM;
        this.passageWidthM = passageWidthM;
        this.buildingAreaM2 = buildingAreaM2;
        this.constructionYear = constructionYear;
        this.existingFloors = existingFloors;
        this.existingUnits = existingUnits;
        this.orientation = orientation;
        this.propertyType = propertyType;
        this.buildingCondition = buildingCondition;
        this.registryMainNo = registryMainNo;
        this.registrySubNo = registrySubNo;
        this.registrySection = registrySection;
        this.postalCode = postalCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.attributesSchemaVersion = "1.0";
        this.attributes = mutableAttributes(attributes);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void update(
            String province,
            String city,
            String district,
            String neighborhood,
            String address,
            BigDecimal landAreaM2,
            BigDecimal frontageM,
            BigDecimal passageWidthM,
            BigDecimal buildingAreaM2,
            Integer constructionYear,
            Integer existingFloors,
            Integer existingUnits,
            String orientation,
            String propertyType,
            String buildingCondition,
            String registryMainNo,
            String registrySubNo,
            String registrySection,
            String postalCode,
            BigDecimal latitude,
            BigDecimal longitude,
            Map<String, Object> attributes
    ) {
        this.province = province;
        this.city = city;
        this.district = district;
        this.neighborhood = neighborhood;
        this.address = address;
        this.landAreaM2 = landAreaM2;
        this.frontageM = frontageM;
        this.passageWidthM = passageWidthM;
        this.buildingAreaM2 = buildingAreaM2;
        this.constructionYear = constructionYear;
        this.existingFloors = existingFloors;
        this.existingUnits = existingUnits;
        this.orientation = orientation;
        this.propertyType = propertyType;
        this.buildingCondition = buildingCondition;
        this.registryMainNo = registryMainNo;
        this.registrySubNo = registrySubNo;
        this.registrySection = registrySection;
        this.postalCode = postalCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.attributes = mutableAttributes(attributes);
        this.updatedAt = Instant.now();
    }

    private Map<String, Object> mutableAttributes(
            Map<String, Object> source
    ) {
        return source == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(source);
    }

    public UUID getId() { return id; }
    public UUID getCaseId() { return caseId; }
    public String getProvince() { return province; }
    public String getCity() { return city; }
    public String getDistrict() { return district; }
    public String getNeighborhood() { return neighborhood; }
    public String getAddress() { return address; }
    public BigDecimal getLandAreaM2() { return landAreaM2; }
    public BigDecimal getFrontageM() { return frontageM; }
    public BigDecimal getPassageWidthM() { return passageWidthM; }
    public BigDecimal getBuildingAreaM2() { return buildingAreaM2; }
    public Integer getConstructionYear() { return constructionYear; }
    public Integer getExistingFloors() { return existingFloors; }
    public Integer getExistingUnits() { return existingUnits; }
    public String getOrientation() { return orientation; }
    public String getPropertyType() { return propertyType; }
    public String getBuildingCondition() { return buildingCondition; }
    public String getRegistryMainNo() { return registryMainNo; }
    public String getRegistrySubNo() { return registrySubNo; }
    public String getRegistrySection() { return registrySection; }
    public String getPostalCode() { return postalCode; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public String getAttributesSchemaVersion() {
        return attributesSchemaVersion;
    }
    public Map<String, Object> getAttributes() {
        return attributes == null
                ? Map.of()
                : Map.copyOf(attributes);
    }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
