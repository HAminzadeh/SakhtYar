package com.sakhtyar.finance.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="financial_analysis")
public class FinancialAnalysisEntity {
    @Id private UUID id;
    @Column(name="feasibility_assessment_id",nullable=false) private UUID feasibilityAssessmentId;
    @Column(name="case_id",nullable=false) private UUID caseId;
    @Column(name="property_id",nullable=false) private UUID propertyId;
    @Column(name="scenario_id",nullable=false) private UUID scenarioId;
    @Column(name="currency_code",nullable=false,length=3) private String currencyCode;
    @Column(name="sellable_area_m2",nullable=false,precision=20,scale=6) private BigDecimal sellableAreaM2;
    @Column(name="expected_sale_price_per_m2",nullable=false,precision=20,scale=2) private BigDecimal expectedSalePricePerM2;
    @Column(name="other_revenue",nullable=false,precision=20,scale=2) private BigDecimal otherRevenue;
    @Column(name="base_construction_cost",nullable=false,precision=20,scale=2) private BigDecimal baseConstructionCost;
    @Column(name="additional_cost",nullable=false,precision=20,scale=2) private BigDecimal additionalCost;
    @Column(name="financing_cost",nullable=false,precision=20,scale=2) private BigDecimal financingCost;
    @Column(name="taxes_and_fees",nullable=false,precision=20,scale=2) private BigDecimal taxesAndFees;
    @Column(name="gross_revenue",nullable=false,precision=20,scale=2) private BigDecimal grossRevenue;
    @Column(name="total_project_cost",nullable=false,precision=20,scale=2) private BigDecimal totalProjectCost;
    @Column(name="projected_profit",nullable=false,precision=20,scale=2) private BigDecimal projectedProfit;
    @Column(name="roi_percent",precision=20,scale=6) private BigDecimal roiPercent;
    @Column(name="profit_margin_percent",precision=20,scale=6) private BigDecimal profitMarginPercent;
    @Column(name="algorithm_version",nullable=false,length=40) private String algorithmVersion;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="input_snapshot",nullable=false,columnDefinition="jsonb")
    private Map<String,Object> inputSnapshot;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="result_snapshot",nullable=false,columnDefinition="jsonb")
    private Map<String,Object> resultSnapshot;
    @Column(name="calculated_by",nullable=false,length=150) private String calculatedBy;
    @Column(name="calculated_at",nullable=false) private Instant calculatedAt;

    protected FinancialAnalysisEntity(){}

    public FinancialAnalysisEntity(UUID id,UUID feasibilityAssessmentId,UUID caseId,UUID propertyId,
            UUID scenarioId,String currencyCode,BigDecimal sellableAreaM2,BigDecimal expectedSalePricePerM2,
            BigDecimal otherRevenue,BigDecimal baseConstructionCost,BigDecimal additionalCost,
            BigDecimal financingCost,BigDecimal taxesAndFees,BigDecimal grossRevenue,
            BigDecimal totalProjectCost,BigDecimal projectedProfit,BigDecimal roiPercent,
            BigDecimal profitMarginPercent,String algorithmVersion,Map<String,Object> inputSnapshot,
            Map<String,Object> resultSnapshot,String calculatedBy,Instant calculatedAt) {
        this.id=id; this.feasibilityAssessmentId=feasibilityAssessmentId; this.caseId=caseId;
        this.propertyId=propertyId; this.scenarioId=scenarioId; this.currencyCode=currencyCode;
        this.sellableAreaM2=sellableAreaM2; this.expectedSalePricePerM2=expectedSalePricePerM2;
        this.otherRevenue=otherRevenue; this.baseConstructionCost=baseConstructionCost;
        this.additionalCost=additionalCost; this.financingCost=financingCost; this.taxesAndFees=taxesAndFees;
        this.grossRevenue=grossRevenue; this.totalProjectCost=totalProjectCost; this.projectedProfit=projectedProfit;
        this.roiPercent=roiPercent; this.profitMarginPercent=profitMarginPercent; this.algorithmVersion=algorithmVersion;
        this.inputSnapshot=inputSnapshot==null?Map.of():Map.copyOf(inputSnapshot);
        this.resultSnapshot=resultSnapshot==null?Map.of():Map.copyOf(resultSnapshot);
        this.calculatedBy=calculatedBy; this.calculatedAt=calculatedAt;
    }

    public UUID getId(){return id;} public UUID getFeasibilityAssessmentId(){return feasibilityAssessmentId;}
    public UUID getCaseId(){return caseId;} public UUID getPropertyId(){return propertyId;}
    public UUID getScenarioId(){return scenarioId;} public String getCurrencyCode(){return currencyCode;}
    public BigDecimal getSellableAreaM2(){return sellableAreaM2;} public BigDecimal getExpectedSalePricePerM2(){return expectedSalePricePerM2;}
    public BigDecimal getOtherRevenue(){return otherRevenue;} public BigDecimal getBaseConstructionCost(){return baseConstructionCost;}
    public BigDecimal getAdditionalCost(){return additionalCost;} public BigDecimal getFinancingCost(){return financingCost;}
    public BigDecimal getTaxesAndFees(){return taxesAndFees;} public BigDecimal getGrossRevenue(){return grossRevenue;}
    public BigDecimal getTotalProjectCost(){return totalProjectCost;} public BigDecimal getProjectedProfit(){return projectedProfit;}
    public BigDecimal getRoiPercent(){return roiPercent;} public BigDecimal getProfitMarginPercent(){return profitMarginPercent;}
    public String getAlgorithmVersion(){return algorithmVersion;}
    public Map<String,Object> getInputSnapshot(){return inputSnapshot==null?Map.of():Map.copyOf(inputSnapshot);}
    public Map<String,Object> getResultSnapshot(){return resultSnapshot==null?Map.of():Map.copyOf(resultSnapshot);}
    public String getCalculatedBy(){return calculatedBy;} public Instant getCalculatedAt(){return calculatedAt;}
}