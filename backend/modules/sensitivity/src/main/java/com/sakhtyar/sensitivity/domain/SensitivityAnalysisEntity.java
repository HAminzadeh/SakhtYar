package com.sakhtyar.sensitivity.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="sensitivity_analysis")
public class SensitivityAnalysisEntity {
    @Id private UUID id;
    @Column(name="financial_analysis_id",nullable=false) private UUID financialAnalysisId;
    @Column(name="case_id",nullable=false) private UUID caseId;
    @Column(name="scenario_id",nullable=false) private UUID scenarioId;
    @Column(name="currency_code",nullable=false,length=3) private String currencyCode;
    @Column(name="point_count",nullable=false) private int pointCount;
    @Column(name="best_profit",precision=20,scale=2) private BigDecimal bestProfit;
    @Column(name="worst_profit",precision=20,scale=2) private BigDecimal worstProfit;
    @Column(name="best_roi_percent",precision=20,scale=6) private BigDecimal bestRoiPercent;
    @Column(name="worst_roi_percent",precision=20,scale=6) private BigDecimal worstRoiPercent;
    @Column(name="algorithm_version",nullable=false,length=40) private String algorithmVersion;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="input_snapshot",nullable=false,columnDefinition="jsonb")
    private Map<String,Object> inputSnapshot;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="summary_snapshot",nullable=false,columnDefinition="jsonb")
    private Map<String,Object> summarySnapshot;
    @Column(name="calculated_by",nullable=false,length=150) private String calculatedBy;
    @Column(name="calculated_at",nullable=false) private Instant calculatedAt;

    protected SensitivityAnalysisEntity(){}

    public SensitivityAnalysisEntity(UUID id,UUID financialAnalysisId,UUID caseId,UUID scenarioId,
            String currencyCode,int pointCount,BigDecimal bestProfit,BigDecimal worstProfit,
            BigDecimal bestRoiPercent,BigDecimal worstRoiPercent,String algorithmVersion,
            Map<String,Object> inputSnapshot,Map<String,Object> summarySnapshot,
            String calculatedBy,Instant calculatedAt) {
        this.id=id; this.financialAnalysisId=financialAnalysisId; this.caseId=caseId;
        this.scenarioId=scenarioId; this.currencyCode=currencyCode; this.pointCount=pointCount;
        this.bestProfit=bestProfit; this.worstProfit=worstProfit; this.bestRoiPercent=bestRoiPercent;
        this.worstRoiPercent=worstRoiPercent; this.algorithmVersion=algorithmVersion;
        this.inputSnapshot=inputSnapshot==null?Map.of():Map.copyOf(inputSnapshot);
        this.summarySnapshot=summarySnapshot==null?Map.of():Map.copyOf(summarySnapshot);
        this.calculatedBy=calculatedBy; this.calculatedAt=calculatedAt;
    }

    public UUID getId(){return id;} public UUID getFinancialAnalysisId(){return financialAnalysisId;}
    public UUID getCaseId(){return caseId;} public UUID getScenarioId(){return scenarioId;}
    public String getCurrencyCode(){return currencyCode;} public int getPointCount(){return pointCount;}
    public BigDecimal getBestProfit(){return bestProfit;} public BigDecimal getWorstProfit(){return worstProfit;}
    public BigDecimal getBestRoiPercent(){return bestRoiPercent;} public BigDecimal getWorstRoiPercent(){return worstRoiPercent;}
    public String getAlgorithmVersion(){return algorithmVersion;}
    public Map<String,Object> getInputSnapshot(){return inputSnapshot==null?Map.of():Map.copyOf(inputSnapshot);}
    public Map<String,Object> getSummarySnapshot(){return summarySnapshot==null?Map.of():Map.copyOf(summarySnapshot);}
    public String getCalculatedBy(){return calculatedBy;} public Instant getCalculatedAt(){return calculatedAt;}
}