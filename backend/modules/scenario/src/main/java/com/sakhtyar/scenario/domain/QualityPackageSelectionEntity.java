package com.sakhtyar.scenario.domain;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="quality_package_selection")
public class QualityPackageSelectionEntity {
 @Id private UUID id; @Column(name="package_id",nullable=false) private UUID packageId; @Column(name="slot_code",nullable=false,length=120) private String slotCode;
 @Column(name="slot_name_fa",nullable=false,length=300) private String slotNameFa; @Column(name="assembly_id",nullable=false) private UUID assemblyId;
 @Column(nullable=false) private boolean required; @Column(name="quantity_multiplier",nullable=false,precision=20,scale=6) private BigDecimal quantityMultiplier;
 @Column(name="sort_order",nullable=false) private int sortOrder; @Column(columnDefinition="text") private String note; @Column(name="created_at",nullable=false) private Instant createdAt;
 protected QualityPackageSelectionEntity(){} public QualityPackageSelectionEntity(UUID id,UUID packageId,String slotCode,String slotNameFa,UUID assemblyId,boolean required,BigDecimal quantityMultiplier,int sortOrder,String note,Instant createdAt){this.id=id;this.packageId=packageId;this.slotCode=slotCode;this.slotNameFa=slotNameFa;this.assemblyId=assemblyId;this.required=required;this.quantityMultiplier=quantityMultiplier;this.sortOrder=sortOrder;this.note=note;this.createdAt=createdAt;}
 public UUID getId(){return id;} public UUID getPackageId(){return packageId;} public String getSlotCode(){return slotCode;} public String getSlotNameFa(){return slotNameFa;} public UUID getAssemblyId(){return assemblyId;} public boolean isRequired(){return required;} public BigDecimal getQuantityMultiplier(){return quantityMultiplier;} public int getSortOrder(){return sortOrder;} public String getNote(){return note;} public Instant getCreatedAt(){return createdAt;}
}