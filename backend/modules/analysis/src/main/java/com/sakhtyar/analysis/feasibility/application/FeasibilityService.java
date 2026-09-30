package com.sakhtyar.analysis.feasibility.application;

import static com.sakhtyar.analysis.feasibility.api.FeasibilityDtos.*;

import com.sakhtyar.analysis.feasibility.domain.*;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.domain.CaseRepository;
import com.sakhtyar.property.domain.*;
import com.sakhtyar.regulation.domain.*;
import com.sakhtyar.scenario.domain.*;
import java.math.*;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeasibilityService {
    private static final String ALGORITHM_VERSION="1.0";

    private final FeasibilityAssessmentRepository assessmentRepo;
    private final FeasibilityReasonRepository reasonRepo;
    private final CaseRepository caseRepo;
    private final PropertyRepository propertyRepo;
    private final ConstructionScenarioRepository scenarioRepo;
    private final ScenarioCostSnapshotRepository costSnapshotRepo;
    private final UrbanEvaluationRepository urbanEvaluationRepo;
    private final AuditService auditService;
    private final FeasibilityCalculator calculator=new FeasibilityCalculator();

    public FeasibilityService(FeasibilityAssessmentRepository assessmentRepo,FeasibilityReasonRepository reasonRepo,
            CaseRepository caseRepo,PropertyRepository propertyRepo,ConstructionScenarioRepository scenarioRepo,
            ScenarioCostSnapshotRepository costSnapshotRepo,UrbanEvaluationRepository urbanEvaluationRepo,
            AuditService auditService) {
        this.assessmentRepo=assessmentRepo; this.reasonRepo=reasonRepo; this.caseRepo=caseRepo;
        this.propertyRepo=propertyRepo; this.scenarioRepo=scenarioRepo; this.costSnapshotRepo=costSnapshotRepo;
        this.urbanEvaluationRepo=urbanEvaluationRepo; this.auditService=auditService;
    }

    @Transactional
    public AssessmentResponse create(CreateAssessmentRequest r,Authentication auth) {
        if(!caseRepo.existsById(r.caseId())) throw new IllegalArgumentException("Construction case not found.");

        PropertyEntity property=propertyRepo.findByCaseId(r.caseId())
                .orElseThrow(()->new IllegalArgumentException("Property for case not found."));

        ConstructionScenarioEntity scenario=scenarioRepo.findById(r.scenarioId())
                .orElseThrow(()->new IllegalArgumentException("Scenario not found."));

        ScenarioCostSnapshotEntity costSnapshot=costSnapshotRepo.findById(r.scenarioCostSnapshotId())
                .orElseThrow(()->new IllegalArgumentException("Scenario cost snapshot not found."));

        UrbanEvaluationEntity urban=urbanEvaluationRepo.findById(r.urbanEvaluationId())
                .orElseThrow(()->new IllegalArgumentException("Urban evaluation not found."));

        if(!costSnapshot.getScenarioId().equals(scenario.getId()))
            throw new IllegalArgumentException("Cost snapshot does not belong to selected scenario.");

        if(!urban.getPropertyId().equals(property.getId()))
            throw new IllegalArgumentException("Urban evaluation does not belong to case property.");

        if(urban.getScenarioId()!=null && !urban.getScenarioId().equals(scenario.getId()))
            throw new IllegalArgumentException("Urban evaluation belongs to a different scenario.");

        Map<String,Object> assumptions=scenario.getAssumptions();
        Integer proposedFloors=integer(assumptions.get("proposedFloors"));
        BigDecimal totalBuiltArea=decimal(assumptions.get("totalBuiltAreaM2"));

        if(proposedFloors==null) proposedFloors=property.getExistingFloors();
        if(totalBuiltArea==null) totalBuiltArea=property.getBuildingAreaM2();

        var calc=calculator.calculate(new FeasibilityCalculator.Input(
                urban.getStatus(),scenario.getStatus(),property.getLandAreaM2(),
                totalBuiltArea,costSnapshot.getTotalCost(),proposedFloors));

        int blockers=(int)calc.reasons().stream().filter(x->x.type()==FeasibilityReasonType.BLOCKER).count();
        int warnings=(int)calc.reasons().stream().filter(x->x.type()==FeasibilityReasonType.WARNING).count();

        Map<String,Object> inputSnapshot=new LinkedHashMap<>();
        inputSnapshot.put("caseId",r.caseId().toString());
        inputSnapshot.put("propertyId",property.getId().toString());
        inputSnapshot.put("scenarioId",scenario.getId().toString());
        inputSnapshot.put("scenarioCostSnapshotId",costSnapshot.getId().toString());
        inputSnapshot.put("urbanEvaluationId",urban.getId().toString());
        put(inputSnapshot,"landAreaM2",text(property.getLandAreaM2()));
        put(inputSnapshot,"totalBuiltAreaM2",text(totalBuiltArea));
        put(inputSnapshot,"proposedFloors",proposedFloors);
        inputSnapshot.put("scenarioStatus",scenario.getStatus().name());
        inputSnapshot.put("regulationStatus",urban.getStatus().name());
        inputSnapshot.put("scenarioCost",costSnapshot.getTotalCost().toPlainString());
        inputSnapshot.put("currencyCode",costSnapshot.getCurrencyCode());

        Map<String,Object> resultSnapshot=new LinkedHashMap<>();
        resultSnapshot.put("status",calc.status().name());
        resultSnapshot.put("readinessScore",calc.readinessScore().toPlainString());
        put(resultSnapshot,"costPerLandM2",text(calc.costPerLandM2()));
        put(resultSnapshot,"costPerBuiltM2",text(calc.costPerBuiltM2()));
        put(resultSnapshot,"calculatedFar",text(calc.calculatedFar()));
        resultSnapshot.put("blockingReasonCount",blockers);
        resultSnapshot.put("warningCount",warnings);

        Instant now=Instant.now();
        var assessment=new FeasibilityAssessmentEntity(
                UUID.randomUUID(),r.caseId(),property.getId(),scenario.getId(),costSnapshot.getId(),
                urban.getId(),calc.status(),calc.readinessScore(),costSnapshot.getTotalCost(),
                costSnapshot.getCurrencyCode(),property.getLandAreaM2(),totalBuiltArea,
                calc.costPerLandM2(),calc.costPerBuiltM2(),proposedFloors,calc.calculatedFar(),
                urban.getStatus().name(),blockers,warnings,ALGORITHM_VERSION,inputSnapshot,resultSnapshot,
                actor(auth),now
        );
        assessmentRepo.save(assessment);

        List<FeasibilityReasonEntity> reasons=new ArrayList<>();
        for(var reason:calc.reasons()) {
            String sourceType = reason.code().startsWith("REGULATION") ? "URBAN_EVALUATION" : "FEASIBILITY_INPUT";
            UUID sourceId = reason.code().startsWith("REGULATION") ? urban.getId() : null;
            Map<String,Object> details=Map.of("scenarioId",scenario.getId().toString());
            reasons.add(reasonRepo.save(new FeasibilityReasonEntity(
                    UUID.randomUUID(),assessment.getId(),reason.type(),reason.code(),reason.message(),
                    sourceType,sourceId,details,now
            )));
        }

        auditService.record("FEASIBILITY_ASSESSMENT",assessment.getId(),"FEASIBILITY_ASSESSED",
                Map.of("caseId",r.caseId().toString(),"scenarioId",scenario.getId().toString(),
                        "status",calc.status().name(),"readinessScore",calc.readinessScore().toPlainString()));

        return toResponse(assessment,reasons);
    }

    @Transactional(readOnly=true)
    public List<AssessmentResponse> byCase(UUID caseId) {
        return assessmentRepo.findByCaseIdOrderByAssessedAtDesc(caseId).stream()
                .map(e->toResponse(e,reasonRepo.findByAssessmentIdOrderByIdAsc(e.getId()))).toList();
    }

    @Transactional(readOnly=true)
    public List<AssessmentResponse> byScenario(UUID scenarioId) {
        return assessmentRepo.findByScenarioIdOrderByAssessedAtDesc(scenarioId).stream()
                .map(e->toResponse(e,reasonRepo.findByAssessmentIdOrderByIdAsc(e.getId()))).toList();
    }

    @Transactional(readOnly=true)
    public AssessmentResponse get(UUID id) {
        var e=assessmentRepo.findById(id).orElseThrow(()->new IllegalArgumentException("Feasibility assessment not found."));
        return toResponse(e,reasonRepo.findByAssessmentIdOrderByIdAsc(id));
    }

    private AssessmentResponse toResponse(FeasibilityAssessmentEntity e,List<FeasibilityReasonEntity> reasons) {
        return new AssessmentResponse(e.getId(),e.getCaseId(),e.getPropertyId(),e.getScenarioId(),
                e.getScenarioCostSnapshotId(),e.getUrbanEvaluationId(),e.getStatus(),e.getReadinessScore(),
                e.getScenarioCost(),e.getCostCurrencyCode(),e.getLandAreaM2(),e.getTotalBuiltAreaM2(),
                e.getCostPerLandM2(),e.getCostPerBuiltM2(),e.getProposedFloors(),e.getCalculatedFar(),
                e.getRegulationStatus(),e.getBlockingReasonCount(),e.getWarningCount(),e.getAlgorithmVersion(),
                e.getInputSnapshot(),e.getResultSnapshot(),e.getAssessedBy(),e.getAssessedAt(),
                reasons.stream().map(ReasonResponse::from).toList());
    }

    private static Integer integer(Object v) {
        if(v==null) return null;
        if(v instanceof Number n) return n.intValue();
        try{return Integer.valueOf(v.toString());}catch(Exception ex){return null;}
    }

    private static BigDecimal decimal(Object v) {
        if(v==null) return null;
        if(v instanceof BigDecimal b) return b;
        if(v instanceof Number n) return new BigDecimal(n.toString());
        try{return new BigDecimal(v.toString());}catch(Exception ex){return null;}
    }

    private static String text(BigDecimal v){return v==null?null:v.stripTrailingZeros().toPlainString();}
    private static void put(Map<String,Object> m,String k,Object v){if(v!=null)m.put(k,v);}
    private static String actor(Authentication a){return a==null||a.getName()==null?"system":a.getName();}
}