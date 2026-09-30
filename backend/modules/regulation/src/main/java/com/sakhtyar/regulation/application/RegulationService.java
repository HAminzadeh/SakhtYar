package com.sakhtyar.regulation.application;

import static com.sakhtyar.regulation.api.RegulationDtos.*;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.knowledge.domain.KnowledgeSourceRepository;
import com.sakhtyar.property.domain.*;
import com.sakhtyar.regulation.domain.*;
import com.sakhtyar.scenario.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegulationService {
    private static final String ALGORITHM_VERSION="1.0";

    private final UrbanRuleRepository ruleRepo;
    private final UrbanEvaluationRepository evaluationRepo;
    private final UrbanEvaluationResultRepository resultRepo;
    private final PropertyRepository propertyRepo;
    private final ConstructionScenarioRepository scenarioRepo;
    private final KnowledgeSourceRepository sourceRepo;
    private final AuditService auditService;
    private final UrbanRuleEvaluator evaluator = new UrbanRuleEvaluator();

    public RegulationService(UrbanRuleRepository ruleRepo,UrbanEvaluationRepository evaluationRepo,
            UrbanEvaluationResultRepository resultRepo,PropertyRepository propertyRepo,
            ConstructionScenarioRepository scenarioRepo,KnowledgeSourceRepository sourceRepo,
            AuditService auditService) {
        this.ruleRepo=ruleRepo; this.evaluationRepo=evaluationRepo; this.resultRepo=resultRepo;
        this.propertyRepo=propertyRepo; this.scenarioRepo=scenarioRepo; this.sourceRepo=sourceRepo;
        this.auditService=auditService;
    }

    @Transactional(readOnly=true)
    public List<RuleResponse> rules() {
        return ruleRepo.findAllByOrderByPriorityAscCodeAsc().stream().map(RuleResponse::from).toList();
    }

    @Transactional
    public RuleResponse createRule(UpsertRuleRequest r,Authentication auth) {
        String code=r.code().trim().toUpperCase(Locale.ROOT);
        ruleRepo.findByCodeIgnoreCase(code).ifPresent(x->{throw new IllegalArgumentException("Urban rule code already exists.");});
        validateRuleRequest(r);
        Instant now=Instant.now();
        var e=new UrbanRuleEntity(UUID.randomUUID(),code,r.nameFa().trim(),trim(r.nameEn()),r.ruleType(),
            upper(r.jurisdictionCountry()),trim(r.jurisdictionProvince()),trim(r.jurisdictionCity()),
            trim(r.jurisdictionDistrict()),trim(r.propertyType()),r.sourceId(),trim(r.sourceUrl()),
            r.validFrom(),r.validTo(),r.priority(),r.active(),r.parameters(),actor(auth),now);
        ruleRepo.save(e);
        auditService.record("URBAN_RULE",e.getId(),"URBAN_RULE_CREATED",Map.of("code",code,"ruleType",r.ruleType().name()));
        return RuleResponse.from(e);
    }

    @Transactional
    public RuleResponse updateRule(UUID id,UpsertRuleRequest r) {
        var e=requireRule(id);
        if(!e.getCode().equalsIgnoreCase(r.code().trim())) throw new IllegalArgumentException("Urban rule code is immutable.");
        validateRuleRequest(r);
        e.update(r.nameFa().trim(),trim(r.nameEn()),r.ruleType(),upper(r.jurisdictionCountry()),
            trim(r.jurisdictionProvince()),trim(r.jurisdictionCity()),trim(r.jurisdictionDistrict()),
            trim(r.propertyType()),r.sourceId(),trim(r.sourceUrl()),r.validFrom(),r.validTo(),
            r.priority(),r.active(),r.parameters(),Instant.now());
        auditService.record("URBAN_RULE",id,"URBAN_RULE_UPDATED",Map.of("code",e.getCode(),"active",e.isActive()));
        return RuleResponse.from(e);
    }

    @Transactional
    public EvaluationResponse evaluate(EvaluateRequest r,Authentication auth) {
        PropertyEntity property=propertyRepo.findById(r.propertyId())
            .orElseThrow(()->new IllegalArgumentException("Property not found."));
        ConstructionScenarioEntity scenario=null;
        if(r.scenarioId()!=null) {
            scenario=scenarioRepo.findById(r.scenarioId())
                .orElseThrow(()->new IllegalArgumentException("Scenario not found."));
        }

        LocalDate today=LocalDate.now();
        List<UrbanRuleEntity> applicable=ruleRepo.findAllByOrderByPriorityAscCodeAsc().stream()
            .filter(UrbanRuleEntity::isActive)
            .filter(x->x.getValidFrom()==null||!today.isBefore(x.getValidFrom()))
            .filter(x->x.getValidTo()==null||!today.isAfter(x.getValidTo()))
            .filter(x->matches(x.getJurisdictionProvince(),property.getProvince()))
            .filter(x->matches(x.getJurisdictionCity(),property.getCity()))
            .filter(x->matches(x.getJurisdictionDistrict(),property.getDistrict()))
            .filter(x->matches(x.getPropertyType(),property.getPropertyType()))
            .toList();

        Map<String,Object> assumptions=scenario==null?Map.of():scenario.getAssumptions();
        Integer proposedFloors=integer(assumptions.get("proposedFloors"));
        BigDecimal totalBuiltArea=decimal(assumptions.get("totalBuiltAreaM2"));
        BigDecimal footprintArea=decimal(assumptions.get("footprintAreaM2"));
        BigDecimal proposedHeight=decimal(assumptions.get("proposedHeightM"));

        if(proposedFloors==null) proposedFloors=property.getExistingFloors();
        if(totalBuiltArea==null) totalBuiltArea=property.getBuildingAreaM2();

        var input=new UrbanRuleEvaluator.EvaluationInput(
            property.getLandAreaM2(),property.getFrontageM(),property.getPassageWidthM(),
            proposedFloors,totalBuiltArea,footprintArea,proposedHeight
        );

        List<PreparedResult> prepared=new ArrayList<>();
        int pass=0,fail=0,review=0;

        for(var rule:applicable) {
            var result=evaluator.evaluate(rule.getRuleType(),rule.getParameters(),input);
            switch(result.outcome()) {
                case PASS -> pass++;
                case FAIL -> fail++;
                case REVIEW -> review++;
            }
            prepared.add(new PreparedResult(rule,result));
        }

        EvaluationStatus status=fail>0?EvaluationStatus.NON_COMPLIANT:
            (review>0?EvaluationStatus.REVIEW_REQUIRED:EvaluationStatus.COMPLIANT);

        Map<String,Object> snapshot=new LinkedHashMap<>();
        put(snapshot,"propertyId",property.getId().toString());
        put(snapshot,"caseId",property.getCaseId().toString());
        put(snapshot,"scenarioId",scenario==null?null:scenario.getId().toString());
        put(snapshot,"province",property.getProvince()); put(snapshot,"city",property.getCity());
        put(snapshot,"district",property.getDistrict()); put(snapshot,"propertyType",property.getPropertyType());
        put(snapshot,"landAreaM2",text(property.getLandAreaM2())); put(snapshot,"frontageM",text(property.getFrontageM()));
        put(snapshot,"passageWidthM",text(property.getPassageWidthM()));
        put(snapshot,"proposedFloors",proposedFloors); put(snapshot,"totalBuiltAreaM2",text(totalBuiltArea));
        put(snapshot,"footprintAreaM2",text(footprintArea)); put(snapshot,"proposedHeightM",text(proposedHeight));

        Instant now=Instant.now();
        var evaluation=new UrbanEvaluationEntity(UUID.randomUUID(),property.getId(),r.scenarioId(),status,
            applicable.size(),pass,fail,review,ALGORITHM_VERSION,snapshot,actor(auth),now);
        evaluationRepo.save(evaluation);

        List<UrbanEvaluationResultEntity> resultEntities=new ArrayList<>();
        for(var p:prepared) {
            Map<String,Object> details=Map.of(
                "ruleCode",p.rule().getCode(),
                "ruleType",p.rule().getRuleType().name(),
                "sourceId",p.rule().getSourceId().toString()
            );
            var entity=new UrbanEvaluationResultEntity(UUID.randomUUID(),evaluation.getId(),p.rule().getId(),
                p.result().outcome(),p.result().actualValue(),p.result().expectedValue(),
                p.result().message(),details,now);
            resultEntities.add(resultRepo.save(entity));
        }

        auditService.record("URBAN_EVALUATION",evaluation.getId(),"URBAN_REGULATION_EVALUATED",
            Map.of("propertyId",property.getId().toString(),"status",status.name(),"ruleCount",applicable.size()));

        return toResponse(evaluation,resultEntities);
    }

    @Transactional(readOnly=true)
    public List<EvaluationResponse> evaluations(UUID propertyId) {
        return evaluationRepo.findByPropertyIdOrderByEvaluatedAtDesc(propertyId).stream()
            .map(e->toResponse(e,resultRepo.findByEvaluationIdOrderByIdAsc(e.getId()))).toList();
    }

    @Transactional(readOnly=true)
    public EvaluationResponse evaluation(UUID id) {
        var e=evaluationRepo.findById(id).orElseThrow(()->new IllegalArgumentException("Urban evaluation not found."));
        return toResponse(e,resultRepo.findByEvaluationIdOrderByIdAsc(id));
    }

    private EvaluationResponse toResponse(UrbanEvaluationEntity e,List<UrbanEvaluationResultEntity> results) {
        return new EvaluationResponse(e.getId(),e.getPropertyId(),e.getScenarioId(),e.getStatus(),
            e.getRuleCount(),e.getPassedCount(),e.getFailedCount(),e.getReviewCount(),e.getAlgorithmVersion(),
            e.getInputSnapshot(),e.getEvaluatedBy(),e.getEvaluatedAt(),
            results.stream().map(ResultResponse::from).toList());
    }

    private void validateRuleRequest(UpsertRuleRequest r) {
        if(!sourceRepo.existsById(r.sourceId())) throw new IllegalArgumentException("Knowledge source not found.");
        if(r.validFrom()!=null&&r.validTo()!=null&&r.validTo().isBefore(r.validFrom()))
            throw new IllegalArgumentException("validTo cannot be before validFrom.");
        if(r.parameters()==null||r.parameters().isEmpty())
            throw new IllegalArgumentException("Rule parameters are required.");
    }

    private UrbanRuleEntity requireRule(UUID id) {
        return ruleRepo.findById(id).orElseThrow(()->new IllegalArgumentException("Urban rule not found."));
    }

    private static boolean matches(String ruleValue,String propertyValue) {
        return ruleValue==null||ruleValue.isBlank()||(propertyValue!=null&&ruleValue.equalsIgnoreCase(propertyValue));
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
    private static String trim(String s){return s==null?null:s.trim();}
    private static String upper(String s){return s==null?null:s.trim().toUpperCase(Locale.ROOT);}
    private static String actor(Authentication a){return a==null||a.getName()==null?"system":a.getName();}
    private record PreparedResult(UrbanRuleEntity rule,UrbanRuleEvaluator.Result result){}
}