package com.sakhtyar.finance.application;

import static com.sakhtyar.finance.api.FinanceDtos.*;

import com.sakhtyar.analysis.feasibility.domain.*;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.builder.domain.*;
import com.sakhtyar.finance.domain.*;
import com.sakhtyar.owner.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialAnalysisService {
    private static final String ALGORITHM_VERSION="1.0";

    private final FinancialAnalysisRepository analysisRepo;
    private final FinancialParticipationAllocationRepository allocationRepo;
    private final FeasibilityAssessmentRepository feasibilityRepo;
    private final OwnerRepository ownerRepo;
    private final ProjectBuilderRepository projectBuilderRepo;
    private final AuditService auditService;
    private final FinancialCalculator calculator=new FinancialCalculator();

    public FinancialAnalysisService(FinancialAnalysisRepository analysisRepo,
            FinancialParticipationAllocationRepository allocationRepo,
            FeasibilityAssessmentRepository feasibilityRepo,OwnerRepository ownerRepo,
            ProjectBuilderRepository projectBuilderRepo,AuditService auditService) {
        this.analysisRepo=analysisRepo; this.allocationRepo=allocationRepo;
        this.feasibilityRepo=feasibilityRepo; this.ownerRepo=ownerRepo;
        this.projectBuilderRepo=projectBuilderRepo; this.auditService=auditService;
    }

    @Transactional
    public FinancialAnalysisResponse create(CreateFinancialAnalysisRequest r,Authentication auth) {
        FeasibilityAssessmentEntity feasibility=feasibilityRepo.findById(r.feasibilityAssessmentId())
                .orElseThrow(()->new IllegalArgumentException("Feasibility assessment not found."));

        if(feasibility.getStatus()==FeasibilityStatus.NOT_FEASIBLE)
            throw new IllegalStateException("Financial analysis cannot be created from a NOT_FEASIBLE assessment.");

        validateParticipants(r.participants(),feasibility);

        List<FinancialCalculator.Participant> calculatorParticipants=new ArrayList<>();
        for(int i=0;i<r.participants().size();i++) {
            var p=r.participants().get(i);
            calculatorParticipants.add(new FinancialCalculator.Participant(
                    Integer.toString(i),p.valueSharePercent(),p.costSharePercent()));
        }

        var result=calculator.calculate(
                new FinancialCalculator.Input(
                        r.sellableAreaM2(),r.expectedSalePricePerM2(),r.otherRevenue(),
                        feasibility.getScenarioCost(),r.additionalCost(),r.financingCost(),r.taxesAndFees()
                ),
                calculatorParticipants
        );

        String currency=r.currencyCode().trim().toUpperCase(Locale.ROOT);
        if(!currency.equalsIgnoreCase(feasibility.getCostCurrencyCode()))
            throw new IllegalArgumentException("Financial analysis currency must match feasibility cost currency.");

        Map<String,Object> inputSnapshot=new LinkedHashMap<>();
        inputSnapshot.put("feasibilityAssessmentId",feasibility.getId().toString());
        inputSnapshot.put("caseId",feasibility.getCaseId().toString());
        inputSnapshot.put("propertyId",feasibility.getPropertyId().toString());
        inputSnapshot.put("scenarioId",feasibility.getScenarioId().toString());
        inputSnapshot.put("currencyCode",currency);
        inputSnapshot.put("sellableAreaM2",r.sellableAreaM2().toPlainString());
        inputSnapshot.put("expectedSalePricePerM2",r.expectedSalePricePerM2().toPlainString());
        inputSnapshot.put("baseConstructionCost",feasibility.getScenarioCost().toPlainString());
        inputSnapshot.put("otherRevenue",r.otherRevenue().toPlainString());
        inputSnapshot.put("additionalCost",r.additionalCost().toPlainString());
        inputSnapshot.put("financingCost",r.financingCost().toPlainString());
        inputSnapshot.put("taxesAndFees",r.taxesAndFees().toPlainString());
        inputSnapshot.put("feasibilityStatus",feasibility.getStatus().name());

        Map<String,Object> resultSnapshot=new LinkedHashMap<>();
        resultSnapshot.put("grossRevenue",result.grossRevenue().toPlainString());
        resultSnapshot.put("totalProjectCost",result.totalProjectCost().toPlainString());
        resultSnapshot.put("projectedProfit",result.projectedProfit().toPlainString());
        put(resultSnapshot,"roiPercent",text(result.roiPercent()));
        put(resultSnapshot,"profitMarginPercent",text(result.profitMarginPercent()));

        Instant now=Instant.now();
        var analysis=new FinancialAnalysisEntity(
                UUID.randomUUID(),feasibility.getId(),feasibility.getCaseId(),feasibility.getPropertyId(),
                feasibility.getScenarioId(),currency,r.sellableAreaM2(),r.expectedSalePricePerM2(),
                r.otherRevenue(),feasibility.getScenarioCost(),r.additionalCost(),r.financingCost(),
                r.taxesAndFees(),result.grossRevenue(),result.totalProjectCost(),result.projectedProfit(),
                result.roiPercent(),result.profitMarginPercent(),ALGORITHM_VERSION,inputSnapshot,
                resultSnapshot,actor(auth),now
        );
        analysisRepo.save(analysis);

        List<FinancialParticipationAllocationEntity> allocations=new ArrayList<>();
        for(int i=0;i<r.participants().size();i++) {
            var request=r.participants().get(i);
            var calculated=result.allocations().get(i);
            allocations.add(allocationRepo.save(new FinancialParticipationAllocationEntity(
                    UUID.randomUUID(),analysis.getId(),request.participantType(),request.participantRefId(),
                    request.participantLabel().trim(),request.valueSharePercent(),request.costSharePercent(),
                    calculated.allocatedRevenue(),calculated.allocatedCost(),calculated.projectedNetValue(),
                    request.metadata(),now
            )));
        }

        auditService.record("FINANCIAL_ANALYSIS",analysis.getId(),"FINANCIAL_ANALYSIS_CALCULATED",
                Map.of("caseId",analysis.getCaseId().toString(),"scenarioId",analysis.getScenarioId().toString(),
                        "projectedProfit",analysis.getProjectedProfit().toPlainString(),"currency",currency));

        return toResponse(analysis,allocations);
    }

    @Transactional(readOnly=true)
    public FinancialAnalysisResponse get(UUID id) {
        var e=analysisRepo.findById(id).orElseThrow(()->new IllegalArgumentException("Financial analysis not found."));
        return toResponse(e,allocationRepo.findByFinancialAnalysisIdOrderByIdAsc(id));
    }

    @Transactional(readOnly=true)
    public List<FinancialAnalysisResponse> byCase(UUID caseId) {
        return analysisRepo.findByCaseIdOrderByCalculatedAtDesc(caseId).stream()
                .map(e->toResponse(e,allocationRepo.findByFinancialAnalysisIdOrderByIdAsc(e.getId()))).toList();
    }

    @Transactional(readOnly=true)
    public List<FinancialAnalysisResponse> byScenario(UUID scenarioId) {
        return analysisRepo.findByScenarioIdOrderByCalculatedAtDesc(scenarioId).stream()
                .map(e->toResponse(e,allocationRepo.findByFinancialAnalysisIdOrderByIdAsc(e.getId()))).toList();
    }

    private void validateParticipants(List<ParticipantRequest> participants,FeasibilityAssessmentEntity feasibility) {
        if(participants==null||participants.isEmpty()) throw new IllegalArgumentException("At least one participant is required.");

        for(var p:participants) {
            if(p.participantType()==ParticipantType.OWNER) {
                if(p.participantRefId()==null) throw new IllegalArgumentException("OWNER participant requires participantRefId.");
                OwnerEntity owner=ownerRepo.findById(p.participantRefId())
                        .orElseThrow(()->new IllegalArgumentException("Owner participant not found."));
                if(!owner.getPropertyId().equals(feasibility.getPropertyId()))
                    throw new IllegalArgumentException("Owner participant does not belong to case property.");
            } else if(p.participantType()==ParticipantType.BUILDER) {
                if(p.participantRefId()==null) throw new IllegalArgumentException("BUILDER participant requires participantRefId.");
                ProjectBuilderEntity builder=projectBuilderRepo.findById(p.participantRefId())
                        .orElseThrow(()->new IllegalArgumentException("Project builder participant not found."));
                if(!builder.getCaseId().equals(feasibility.getCaseId()))
                    throw new IllegalArgumentException("Builder participant does not belong to case.");
            }
        }
    }

    private FinancialAnalysisResponse toResponse(FinancialAnalysisEntity e,List<FinancialParticipationAllocationEntity> allocations) {
        return new FinancialAnalysisResponse(
                e.getId(),e.getFeasibilityAssessmentId(),e.getCaseId(),e.getPropertyId(),e.getScenarioId(),
                e.getCurrencyCode(),e.getSellableAreaM2(),e.getExpectedSalePricePerM2(),e.getOtherRevenue(),
                e.getBaseConstructionCost(),e.getAdditionalCost(),e.getFinancingCost(),e.getTaxesAndFees(),
                e.getGrossRevenue(),e.getTotalProjectCost(),e.getProjectedProfit(),e.getRoiPercent(),
                e.getProfitMarginPercent(),e.getAlgorithmVersion(),e.getInputSnapshot(),e.getResultSnapshot(),
                e.getCalculatedBy(),e.getCalculatedAt(),allocations.stream().map(AllocationResponse::from).toList()
        );
    }

    private static String actor(Authentication a){return a==null||a.getName()==null?"system":a.getName();}
    private static String text(BigDecimal v){return v==null?null:v.stripTrailingZeros().toPlainString();}
    private static void put(Map<String,Object> m,String k,Object v){if(v!=null)m.put(k,v);}
}