package com.sakhtyar.regulation.application;

import static com.sakhtyar.regulation.api.RegulationDtos.*;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.application.CaseAccessService;
import com.sakhtyar.knowledge.domain.KnowledgeSourceRepository;
import com.sakhtyar.globalization.application.JurisdictionService;
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
    private static final String ALGORITHM_VERSION="1.1-phase17b";

    private final UrbanRuleRepository ruleRepo;
    private final UrbanEvaluationRepository evaluationRepo;
    private final UrbanEvaluationResultRepository resultRepo;
    private final PropertyRepository propertyRepo;
    private final ConstructionScenarioRepository scenarioRepo;
    private final KnowledgeSourceRepository sourceRepo;
    private final AuditService auditService;
    private final JurisdictionService jurisdictionService;
    private final RegulationEvidenceService evidence;
    private final CaseAccessService caseAccess;
    private final UrbanRuleEvaluator evaluator = new UrbanRuleEvaluator();

    public RegulationService(UrbanRuleRepository ruleRepo,UrbanEvaluationRepository evaluationRepo,
            UrbanEvaluationResultRepository resultRepo,PropertyRepository propertyRepo,
            ConstructionScenarioRepository scenarioRepo,KnowledgeSourceRepository sourceRepo,
            AuditService auditService,JurisdictionService jurisdictionService,RegulationEvidenceService evidence,
            CaseAccessService caseAccess) {
        this.ruleRepo=ruleRepo; this.evaluationRepo=evaluationRepo; this.resultRepo=resultRepo;
        this.propertyRepo=propertyRepo; this.scenarioRepo=scenarioRepo; this.sourceRepo=sourceRepo;
        this.auditService=auditService;
        this.jurisdictionService=jurisdictionService;
        this.evidence=evidence;
        this.caseAccess=caseAccess;
    }

    private static void requireRuleManager() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getAuthorities() == null || auth.getAuthorities().stream()
                .noneMatch(a -> "USER_MANAGE".equals(a.getAuthority()))) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only a regulation manager may change urban rules.");
        }
    }

    @Transactional(readOnly=true)
    public List<RuleResponse> rules() {
        return ruleRepo.findAllByOrderByPriorityAscCodeAsc().stream().map(RuleResponse::from).toList();
    }

    @Transactional
    public RuleResponse createRule(UpsertRuleRequest r,Authentication auth) {
        requireRuleManager();
        String code=r.code().trim().toUpperCase(Locale.ROOT);
        ruleRepo.findByCodeIgnoreCase(code).ifPresent(x->{throw new IllegalArgumentException("Urban rule code already exists.");});
        validateRuleRequest(r);
        Instant now=Instant.now();
        var e=new UrbanRuleEntity(UUID.randomUUID(),code,r.nameFa().trim(),trim(r.nameEn()),r.ruleType(),
            upper(r.jurisdictionCountry()),trim(r.jurisdictionProvince()),trim(r.jurisdictionCity()),
            trim(r.jurisdictionDistrict()),trim(r.propertyType()),r.sourceId(),trim(r.sourceUrl()),
            r.validFrom(),r.validTo(),r.priority(),false,r.parameters(),actor(auth),now);
        ruleRepo.save(e);
        auditService.record("URBAN_RULE",e.getId(),"URBAN_RULE_CREATED",Map.of("code",code,"ruleType",r.ruleType().name()));
        return RuleResponse.from(e);
    }

    @Transactional
    public RuleResponse updateRule(UUID id,UpsertRuleRequest r) {
        requireRuleManager();
        var e=requireRule(id);
        if(!e.getCode().equalsIgnoreCase(r.code().trim())) throw new IllegalArgumentException("Urban rule code is immutable.");
        validateRuleRequest(r);
        e.update(r.nameFa().trim(),trim(r.nameEn()),r.ruleType(),upper(r.jurisdictionCountry()),
            trim(r.jurisdictionProvince()),trim(r.jurisdictionCity()),trim(r.jurisdictionDistrict()),
            trim(r.propertyType()),r.sourceId(),trim(r.sourceUrl()),r.validFrom(),r.validTo(),
            r.priority(),r.active(),r.parameters(),Instant.now());
        auditService.record("URBAN_RULE",id,"URBAN_RULE_UPDATED",Map.of("code",e.getCode(),
            "active",e.isActive(),"bindingState",e.getVerifiedKnowledgeRuleId()==null?"UNBOUND":"REVIEWED"));
        return RuleResponse.from(e);
    }

    @Transactional
    public EvaluationResponse evaluate(EvaluateRequest r,Authentication auth) {
        PropertyEntity property=propertyRepo.findById(r.propertyId())
            .orElseThrow(()->new IllegalArgumentException("Property not found."));
        // Access is enforced before reading scenario assumptions or any evidence.
        caseAccess.requireWrite(property.getCaseId());
        if (!"REAL".equals(property.getDataScope()))
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Property not found.");
        ConstructionScenarioEntity scenario=null;
        if(r.scenarioId()!=null) {
            scenario=scenarioRepo.findById(r.scenarioId())
                .orElseThrow(()->new IllegalArgumentException("Scenario not found."));
        }

        LocalDate today=LocalDate.now();
        Set<UUID> jurisdictionRuleIds=jurisdictionService.matchingUrbanRules(property.getCaseId());
        Set<UUID> authorizedRuleIds=evidence.authorizedUrbanRuleIds(property.getCaseId());
        List<UrbanRuleEntity> applicable=ruleRepo.findAllByOrderByPriorityAscCodeAsc().stream()
            .filter(UrbanRuleEntity::isActive)
            .filter(x->x.getValidFrom()==null||!today.isBefore(x.getValidFrom()))
            .filter(x->x.getValidTo()==null||!today.isAfter(x.getValidTo()))
            .filter(x->jurisdictionRuleIds.contains(x.getId()))
            .filter(x->authorizedRuleIds.contains(x.getId()))
            .filter(x->matches(x.getJurisdictionDistrict(),property.getDistrict()))
            .filter(x->matches(x.getPropertyType(),property.getPropertyType()))
            .toList();

        // Explicitly mark overlapping, non-identical legal limits as unresolved;
        // priority order alone cannot establish legal superiority.
        Set<UUID> conflictingRuleIds=UrbanRuleConflictPolicy.conflicts(applicable);
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

        // Until the full set of applicable obligations has been legally certified,
        // passing known rules does not prove project-wide regulatory compliance.
        EvaluationStatus status=applicable.isEmpty() || !conflictingRuleIds.isEmpty() ?
            EvaluationStatus.REVIEW_REQUIRED : fail>0?EvaluationStatus.NON_COMPLIANT:
            EvaluationStatus.REVIEW_REQUIRED;

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
        snapshot.put("coverageDecision","REVIEW_REQUIRED_UNTIL_LEGAL_SCOPE_CERTIFIED");
        snapshot.put("conflictRuleIds",conflictingRuleIds.stream().map(UUID::toString).sorted().toList());
        List<Map<String,Object>> ruleSnapshots=new ArrayList<>();
        for (var p:prepared) {
            UrbanRuleEntity rule=p.rule();
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("id",rule.getId().toString());
            item.put("code",rule.getCode());
            item.put("type",rule.getRuleType().name());
            item.put("parameters",new LinkedHashMap<>(rule.getParameters()));
            item.put("country",Objects.toString(rule.getJurisdictionCountry(),""));
            item.put("province",Objects.toString(rule.getJurisdictionProvince(),""));
            item.put("city",Objects.toString(rule.getJurisdictionCity(),""));
            item.put("district",Objects.toString(rule.getJurisdictionDistrict(),""));
            item.put("propertyType",Objects.toString(rule.getPropertyType(),""));
            item.put("validFrom",Objects.toString(rule.getValidFrom(),""));
            item.put("validTo",Objects.toString(rule.getValidTo(),""));
            item.put("sourceId",rule.getSourceId().toString());
            item.put("verifiedKnowledgeRuleId",Objects.toString(rule.getVerifiedKnowledgeRuleId(),""));
            item.put("updatedAt",Objects.toString(rule.getUpdatedAt(),""));
            item.put("outcome",p.result().outcome().name());
            item.put("conflict",conflictingRuleIds.contains(rule.getId()));
            ruleSnapshots.add(item);
        }
        snapshot.put("ruleSnapshots",ruleSnapshots);

        Instant now=Instant.now();
        var evaluation=new UrbanEvaluationEntity(UUID.randomUUID(),property.getId(),r.scenarioId(),status,
            applicable.size(),pass,fail,review,ALGORITHM_VERSION,snapshot,actor(auth),now);
        evaluationRepo.save(evaluation);

        List<UrbanEvaluationResultEntity> resultEntities=new ArrayList<>();
        for(var p:prepared) {
            Map<String,Object> details=new LinkedHashMap<>();
            details.put("ruleCode",p.rule().getCode());
            details.put("ruleType",p.rule().getRuleType().name());
            details.put("sourceId",p.rule().getSourceId().toString());
            details.put("verifiedKnowledgeRuleId",Objects.toString(p.rule().getVerifiedKnowledgeRuleId(),""));
            details.put("ruleParameters",new LinkedHashMap<>(p.rule().getParameters()));
            details.put("ruleUpdatedAt",Objects.toString(p.rule().getUpdatedAt(),""));
            details.put("conflict",conflictingRuleIds.contains(p.rule().getId()));
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
        var property=propertyRepo.findById(propertyId)
            .orElseThrow(()->new IllegalArgumentException("Property not found."));
        caseAccess.requireRead(property.getCaseId());
        if (!"REAL".equals(property.getDataScope()))
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Property not found.");
        return evaluationRepo.findByPropertyIdOrderByEvaluatedAtDesc(propertyId).stream()
            .map(e->toResponse(e,resultRepo.findByEvaluationIdOrderByIdAsc(e.getId()))).toList();
    }

    @Transactional(readOnly=true)
    public EvaluationResponse evaluation(UUID id) {
        var e=evaluationRepo.findById(id).orElseThrow(()->new IllegalArgumentException("Urban evaluation not found."));
        var property=propertyRepo.findById(e.getPropertyId())
            .orElseThrow(()->new IllegalArgumentException("Property not found."));
        caseAccess.requireRead(property.getCaseId());
        if (!"REAL".equals(property.getDataScope()))
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Property not found.");
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
        try {return new BigDecimal(v.toString()).intValueExact();}
        catch (RuntimeException ex) {return null;}
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