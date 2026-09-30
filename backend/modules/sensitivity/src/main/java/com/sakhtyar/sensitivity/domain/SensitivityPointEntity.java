package com.sakhtyar.sensitivity.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="sensitivity_point")
public class SensitivityPointEntity {
    @Id private UUID id;
    @Column(name="sensitivity_analysis_id",nullable=false) private UUID sensitivityAnalysisId;
    @Column(name="sequence_no",nullable=false) private int sequenceNo;
    @Column(name="sale_price_change_percent",nullable=false,precision=12,scale=6) private BigDecimal salePriceChangePercent;
    @Column(name="sellable_area_change_percent",nullable=false,precision=12,scale=6) private BigDecimal sellableAreaChangePercent;
    @Column(name="construction_cost_change_percent",nullable=false,precision=12,scale=6) private BigDecimal constructionCostChangePercent;
    @Column(name="adjusted_sellable_area_m2",nullable=false,precision=20,scale=6) private BigDecimal adjustedSellableAreaM2;
    @Column(name="adjusted_sale_price_per_m2",nullable=false,precision=20,scale=2) private BigDecimal adjustedSalePricePerM2;
    @Column(name="adjusted_base_construction_cost",nullable=false,precision=20,scale=2) private BigDecimal adjustedBaseConstructionCost;
    @Column(name="gross_revenue",nullable=false,precision=20,scale=2) private BigDecimal grossRevenue;
    @Column(name="total_project_cost",nullable=false,precision=20,scale=2) private BigDecimal totalProjectCost;
    @Column(name="projected_profit",nullable=false,precision=20,scale=2) private BigDecimal projectedProfit;
    @Column(name="roi_percent",precision=20,scale=6) private BigDecimal roiPercent;
    @Column(name="profit_margin_percent",precision=20,scale=6) private BigDecimal profitMarginPercent;
    @Column(name="created_at",nullable=false) private Instant createdAt;

    protected SensitivityPointEntity(){}

    public SensitivityPointEntity(UUID id,UUID sensitivityAnalysisId,int sequenceNo,
            BigDecimal salePriceChangePercent,BigDecimal sellableAreaChangePercent,
            BigDecimal constructionCostChangePercent,BigDecimal adjustedSellableAreaM2,
            BigDecimal adjustedSalePricePerM2,BigDecimal adjustedBaseConstructionCost,
            BigDecimal grossRevenue,BigDecimal totalProjectCost,BigDecimal projectedProfit,
            BigDecimal roiPercent,BigDecimal profitMarginPercent,Instant createdAt) {
        this.id=id; this.sensitivityAnalysisId=sensitivityAnalysisId; this.sequenceNo=sequenceNo;
        this.salePriceChangePercent=salePriceChangePercent; this.sellableAreaChangePercent=sellableAreaChangePercent;
        this.constructionCostChangePercent=constructionCostChangePercent; this.adjustedSellableAreaM2=adjustedSellableAreaM2;
        this.adjustedSalePricePerM2=adjustedSalePricePerM2; this.adjustedBaseConstructionCost=adjustedBaseConstructionCost;
        this.grossRevenue=grossRevenue; this.totalProjectCost=totalProjectCost; this.projectedProfit=projectedProfit;
        this.roiPercent=roiPercent; this.profitMarginPercent=profitMarginPercent; this.createdAt=createdAt;
    }

    public UUID getId(){return id;} public UUID getSensitivityAnalysisId(){return sensitivityAnalysisId;}
    public int getSequenceNo(){return sequenceNo;} public BigDecimal getSalePriceChangePercent(){return salePriceChangePercent;}
    public BigDecimal getSellableAreaChangePercent(){return sellableAreaChangePercent;}
    public BigDecimal getConstructionCostChangePercent(){return constructionCostChangePercent;}
    public BigDecimal getAdjustedSellableAreaM2(){return adjustedSellableAreaM2;}
    public BigDecimal getAdjustedSalePricePerM2(){return adjustedSalePricePerM2;}
    public BigDecimal getAdjustedBaseConstructionCost(){return adjustedBaseConstructionCost;}
    public BigDecimal getGrossRevenue(){return grossRevenue;} public BigDecimal getTotalProjectCost(){return totalProjectCost;}
    public BigDecimal getProjectedProfit(){return projectedProfit;} public BigDecimal getRoiPercent(){return roiPercent;}
    public BigDecimal getProfitMarginPercent(){return profitMarginPercent;} public Instant getCreatedAt(){return createdAt;}
}