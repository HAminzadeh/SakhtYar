package com.sakhtyar.sensitivity.application;

import static com.sakhtyar.sensitivity.api.SensitivityDtos.*;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.finance.domain.*;
import com.sakhtyar.sensitivity.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SensitivityService {
    private static final String ALGORITHM_VERSION="1.0";

    private final SensitivityAnalysisRepository analysisRepo;
    private final SensitivityPointRepository pointRepo;
    private final FinancialAnalysisRepository financialRepo;
    private final AuditService auditService;
    private final SensitivityCalculator calculator=new SensitivityCalculator();

    public SensitivityService(SensitivityAnalysisRepository analysisRepo,SensitivityPointRepository pointRepo,
            FinancialAnalysisRepository financialRepo,AuditService auditService) {
        this.analysisRepo=analysisRepo; this.pointRepo=pointRepo;
        this.financialRepo=financialRepo; this.auditService=auditService;
    }

    @Transactional
    public SensitivityResponse create(CreateSensitivityRequest r,Authentication auth) {
        FinancialAnalysisEntity base=financialRepo.findById(r.financialAnalysisId())
                .orElseThrow(()->new IllegalArgumentException("Financial analysis not found."));

        if(r.points()==null || r.points().isEmpty()) throw new IllegalArgumentException("At least one sensitivity point is required.");
        if(r.points().size()>250) throw new IllegalArgumentException("Maximum 250 sensitivity points are allowed.");

        var baseline=new SensitivityCalculator.Baseline(
                base.getSellableAreaM2(),base.getExpectedSalePricePerM2(),base.getOtherRevenue(),
                base.getBaseConstructionCost(),base.getAdditionalCost(),base.getFinancingCost(),base.getTaxesAndFees());

        List<CalculatedPoint> calculated=new ArrayList<>();
        for(int i=0;i<r.points().size();i++) {
            var request=r.points().get(i);
            var result=calculator.calculate(baseline,new SensitivityCalculator.Shock(
                    request.salePriceChangePercent(),request.sellableAreaChangePercent(),
                    request.constructionCostChangePercent()));
            calculated.add(new CalculatedPoint(i+1,request,result));
        }

        BigDecimal bestProfit=calculated.stream().map(x->x.result().projectedProfit()).max(BigDecimal::compareTo).orElse(null);
        BigDecimal worstProfit=calculated.stream().map(x->x.result().projectedProfit()).min(BigDecimal::compareTo).orElse(null);
        BigDecimal bestRoi=calculated.stream().map(x->x.result().roiPercent()).filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(null);
        BigDecimal worstRoi=calculated.stream().map(x->x.result().roiPercent()).filter(Objects::nonNull).min(BigDecimal::compareTo).orElse(null);

        Map<String,Object> input=new LinkedHashMap<>();
        input.put("financialAnalysisId",base.getId().toString());
        input.put("baselineSellableAreaM2",base.getSellableAreaM2().toPlainString());
        input.put("baselineSalePricePerM2",base.getExpectedSalePricePerM2().toPlainString());
        input.put("baselineBaseConstructionCost",base.getBaseConstructionCost().toPlainString());
        input.put("currencyCode",base.getCurrencyCode());
        input.put("pointCount",calculated.size());

        Map<String,Object> summary=new LinkedHashMap<>();
        put(summary,"bestProfit",text(bestProfit)); put(summary,"worstProfit",text(worstProfit));
        put(summary,"bestRoiPercent",text(bestRoi)); put(summary,"worstRoiPercent",text(worstRoi));

        Instant now=Instant.now();
        var analysis=new SensitivityAnalysisEntity(
                UUID.randomUUID(),base.getId(),base.getCaseId(),base.getScenarioId(),base.getCurrencyCode(),
                calculated.size(),bestProfit,worstProfit,bestRoi,worstRoi,ALGORITHM_VERSION,
                input,summary,actor(auth),now
        );
        analysisRepo.save(analysis);

        List<SensitivityPointEntity> entities=new ArrayList<>();
        for(var c:calculated) {
            var q=c.request(); var p=c.result();
            entities.add(pointRepo.save(new SensitivityPointEntity(
                    UUID.randomUUID(),analysis.getId(),c.sequenceNo(),
                    q.salePriceChangePercent(),q.sellableAreaChangePercent(),q.constructionCostChangePercent(),
                    p.adjustedSellableAreaM2(),p.adjustedSalePricePerM2(),p.adjustedBaseConstructionCost(),
                    p.grossRevenue(),p.totalProjectCost(),p.projectedProfit(),p.roiPercent(),
                    p.profitMarginPercent(),now
            )));
        }

        auditService.record("SENSITIVITY_ANALYSIS",analysis.getId(),"SENSITIVITY_ANALYSIS_CALCULATED",
                Map.of("financialAnalysisId",base.getId().toString(),"pointCount",calculated.size()));

        return toResponse(analysis,entities);
    }

    @Transactional(readOnly=true)
    public SensitivityResponse get(UUID id) {
        var e=analysisRepo.findById(id).orElseThrow(()->new IllegalArgumentException("Sensitivity analysis not found."));
        return toResponse(e,pointRepo.findBySensitivityAnalysisIdOrderBySequenceNoAsc(id));
    }

    @Transactional(readOnly=true)
    public List<SensitivityResponse> byFinancialAnalysis(UUID financialAnalysisId) {
        return analysisRepo.findByFinancialAnalysisIdOrderByCalculatedAtDesc(financialAnalysisId).stream()
                .map(e->toResponse(e,pointRepo.findBySensitivityAnalysisIdOrderBySequenceNoAsc(e.getId()))).toList();
    }

    @Transactional(readOnly=true)
    public List<SensitivityResponse> byCase(UUID caseId) {
        return analysisRepo.findByCaseIdOrderByCalculatedAtDesc(caseId).stream()
                .map(e->toResponse(e,pointRepo.findBySensitivityAnalysisIdOrderBySequenceNoAsc(e.getId()))).toList();
    }

    private SensitivityResponse toResponse(SensitivityAnalysisEntity e,List<SensitivityPointEntity> points) {
        return new SensitivityResponse(e.getId(),e.getFinancialAnalysisId(),e.getCaseId(),e.getScenarioId(),
                e.getCurrencyCode(),e.getPointCount(),e.getBestProfit(),e.getWorstProfit(),
                e.getBestRoiPercent(),e.getWorstRoiPercent(),e.getAlgorithmVersion(),e.getInputSnapshot(),
                e.getSummarySnapshot(),e.getCalculatedBy(),e.getCalculatedAt(),
                points.stream().map(PointResponse::from).toList());
    }

    private static String actor(Authentication a){return a==null||a.getName()==null?"system":a.getName();}
    private static String text(BigDecimal v){return v==null?null:v.stripTrailingZeros().toPlainString();}
    private static void put(Map<String,Object> m,String k,Object v){if(v!=null)m.put(k,v);}
    private record CalculatedPoint(int sequenceNo,ShockRequest request,SensitivityCalculator.Point result){}
}