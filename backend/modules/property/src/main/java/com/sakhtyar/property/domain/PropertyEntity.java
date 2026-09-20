package com.sakhtyar.property.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
            String registryMainNo,
            String registrySubNo,
            String registrySection,
            String postalCode,
            BigDecimal latitude,
            BigDecimal longitude,
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
        this.registryMainNo = registryMainNo;
        this.registrySubNo = registrySubNo;
        this.registrySection = registrySection;
        this.postalCode = postalCode;
        this.latitude = latitude;
        this.longitude = longitude;
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
            String registryMainNo,
            String registrySubNo,
            String registrySection,
            String postalCode,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        this.province = province;
        this.city = city;
        this.district = district;
        this.neighborhood = neighborhood;
        this.address = address;
        this.landAreaM2 = landAreaM2;
        this.registryMainNo = registryMainNo;
        this.registrySubNo = registrySubNo;
        this.registrySection = registrySection;
        this.postalCode = postalCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCaseId() { return caseId; }
    public String getProvince() { return province; }
    public String getCity() { return city; }
    public String getDistrict() { return district; }
    public String getNeighborhood() { return neighborhood; }
    public String getAddress() { return address; }
    public BigDecimal getLandAreaM2() { return landAreaM2; }
    public String getRegistryMainNo() { return registryMainNo; }
    public String getRegistrySubNo() { return registrySubNo; }
    public String getRegistrySection() { return registrySection; }
    public String getPostalCode() { return postalCode; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
