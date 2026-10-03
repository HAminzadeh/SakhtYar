package com.sakhtyar.scenario.domain;
import com.sakhtyar.material.domain.MaterialPriceType; import jakarta.persistence.*; import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="scenario_cost_snapshot")
public class ScenarioCostSnapshotEntity {
 @Id private UUID id; @Column(name="scenario_id",nullable=false) private UUID scenarioId; @Enumerated(EnumType.STRING) @Column(name="price_type",nullable=false,length=40) private MaterialPriceType priceType; @Column(name="currency_code",nullable=false,length=8) private String currencyCode;
    @Column(name="currency_id", insertable=false, updatable=false)
    private UUID currencyId; @Column(length=160) private String province; @Column(length=160) private String city; @Column(name="total_cost",nullable=false,precision=20,scale=2) private BigDecimal totalCost; @Column(name="algorithm_version",nullable=false,length=40) private String algorithmVersion; @Column(name="calculated_by",nullable=false,length=150) private String calculatedBy; @Column(name="calculated_at",nullable=false) private Instant calculatedAt;
 protected ScenarioCostSnapshotEntity(){} public ScenarioCostSnapshotEntity(UUID id,UUID scenarioId,MaterialPriceType priceType,String currencyCode,String province,String city,BigDecimal totalCost,String algorithmVersion,String calculatedBy,Instant calculatedAt){this.id=id;this.scenarioId=scenarioId;this.priceType=priceType;this.currencyCode=currencyCode;this.province=province;this.city=city;this.totalCost=totalCost;this.algorithmVersion=algorithmVersion;this.calculatedBy=calculatedBy;this.calculatedAt=calculatedAt;}
 public UUID getId(){return id;} public UUID getScenarioId(){return scenarioId;} public MaterialPriceType getPriceType(){return priceType;} public String getCurrencyCode(){return currencyCode;} public String getProvince(){return province;} public String getCity(){return city;} public BigDecimal getTotalCost(){return totalCost;} public String getAlgorithmVersion(){return algorithmVersion;} public String getCalculatedBy(){return calculatedBy;} public Instant getCalculatedAt(){return calculatedAt;}

    public UUID getCurrencyId(){return currencyId;}
}