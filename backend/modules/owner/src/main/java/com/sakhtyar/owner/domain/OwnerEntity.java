package com.sakhtyar.owner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "property_owner")
public class OwnerEntity {

    @Id
    private UUID id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 150)
    private String lastName;

    @Column(name = "national_id", length = 20)
    private String nationalId;

    @Column(length = 30)
    private String mobile;

    @Column(name = "ownership_numerator", nullable = false)
    private int ownershipNumerator;

    @Column(name = "ownership_denominator", nullable = false)
    private int ownershipDenominator;

    @Column(name = "is_primary_contact", nullable = false)
    private boolean primaryContact;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OwnerEntity() {
    }

    public OwnerEntity(
            UUID id,
            UUID propertyId,
            String firstName,
            String lastName,
            String nationalId,
            String mobile,
            int ownershipNumerator,
            int ownershipDenominator,
            boolean primaryContact,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.propertyId = propertyId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.nationalId = nationalId;
        this.mobile = mobile;
        this.ownershipNumerator = ownershipNumerator;
        this.ownershipDenominator = ownershipDenominator;
        this.primaryContact = primaryContact;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void update(
            String firstName,
            String lastName,
            String nationalId,
            String mobile,
            int ownershipNumerator,
            int ownershipDenominator,
            boolean primaryContact
    ) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.nationalId = nationalId;
        this.mobile = mobile;
        this.ownershipNumerator = ownershipNumerator;
        this.ownershipDenominator = ownershipDenominator;
        this.primaryContact = primaryContact;
        this.updatedAt = Instant.now();
    }

    public void setPrimaryContact(boolean primaryContact) {
        this.primaryContact = primaryContact;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPropertyId() { return propertyId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getNationalId() { return nationalId; }
    public String getMobile() { return mobile; }
    public int getOwnershipNumerator() { return ownershipNumerator; }
    public int getOwnershipDenominator() { return ownershipDenominator; }
    public boolean isPrimaryContact() { return primaryContact; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
