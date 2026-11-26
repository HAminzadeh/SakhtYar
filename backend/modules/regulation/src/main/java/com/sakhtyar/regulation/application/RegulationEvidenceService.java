package com.sakhtyar.regulation.application;

import com.sakhtyar.casefile.application.CaseAccessService;
import com.sakhtyar.globalization.application.JurisdictionService;
import com.sakhtyar.property.domain.PropertyEntity;
import com.sakhtyar.property.domain.PropertyRepository;
import com.sakhtyar.regulation.domain.UrbanRuleEntity;
import com.sakhtyar.regulation.domain.UrbanRuleRepository;
import com.sakhtyar.regulation.domain.UrbanRuleType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class RegulationEvidenceService {
    private final JdbcTemplate jdbc;
    private final PropertyRepository properties;
    private final UrbanRuleRepository rules;
    private final CaseAccessService caseAccess;
    private final JurisdictionService jurisdictions;
    public RegulationEvidenceService(JdbcTemplate jdbc,PropertyRepository properties,UrbanRuleRepository rules,
            CaseAccessService caseAccess,JurisdictionService jurisdictions){
        this.jdbc=jdbc;this.properties=properties;this.rules=rules;
        this.caseAccess=caseAccess;this.jurisdictions=jurisdictions;
    }
    private static boolean matches(String scope,String value){return scope==null||scope.isBlank()||(value!=null&&scope.trim().equalsIgnoreCase(value.trim()));}
    private static LocalDate asDate(Object raw){
        if(raw==null)return null;
        if(raw instanceof LocalDate d)return d;
        if(raw instanceof java.sql.Date d)return d.toLocalDate();
        return LocalDate.parse(raw.toString());
    }
    private PropertyEntity property(UUID caseId){
        caseAccess.requireRead(caseId);
        PropertyEntity p=properties.findByCaseId(caseId)
            .orElseThrow(()->new IllegalArgumentException("Property not found for case"));
        if(!"REAL".equals(p.getDataScope())) throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND,"Property not found for case");
        return p;
    }
    private static void requireRuleManager(String reviewer){
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !auth.isAuthenticated() || auth.getAuthorities()==null ||
           auth.getAuthorities().stream().noneMatch(a->"USER_MANAGE".equals(a.getAuthority())) ||
           reviewer==null || !reviewer.equals(auth.getName()))
            throw new org.springframework.security.access.AccessDeniedException("USER_MANAGE required");
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> evidence(UUID caseId) {
        PropertyEntity p=property(caseId);
        String country=jurisdictions.forCase(caseId).countryCode();
        // Unknown country cannot be treated as IR: fail closed and request location review.
        if (country == null || country.isBlank()) return List.of();
        LocalDate now=LocalDate.now();
        // Scope, validity and provenance checks MUST precede the limit.
        List<Map<String,Object>> rows=jdbc.queryForList("""
             select r.id as rule_id,r.stable_key,r.numeric_value,r.operator,rv.subject_code,rv.unit_code,
               rv.verified_operator,
               rv.scope_country,rv.scope_city,rv.scope_district,rv.source_authority,
               rv.source_sha256,rv.source_page,rv.valid_from,rv.valid_to,rv.reviewer,
               coalesce(nullif(d.filename,''),d.canonical_path) as original_name,rel.id as release_id,rel.release_key
             from knowledge_rule r join knowledge_rule_review rv on rv.rule_id=r.id and rv.decision='APPROVED'
             join knowledge_release rel on rel.knowledge_version_id=r.knowledge_version_id and rel.status='PUBLISHED'
             join knowledge_source_document d on d.id=rv.source_document_id
             where r.status='VERIFIED' and rv.source_page>0 and rv.source_sha256=d.sha256
               and upper(rv.scope_country)=upper(?)
               and (nullif(btrim(rv.scope_city),'') is null or lower(btrim(rv.scope_city))=lower(?))
               and (nullif(btrim(rv.scope_district),'') is null or lower(btrim(rv.scope_district))=lower(?))
               and (rv.valid_from is null or rv.valid_from <= ?)
               and (rv.valid_to is null or rv.valid_to >= ?)
             order by rel.published_at desc,r.id limit 5001
             """,country,p.getCity(),p.getDistrict(),now,now);
        if(rows.size()>5000) throw new IllegalStateException(
                "Matching legal evidence exceeds safe bound; narrow the scope before evaluating.");
        return rows;
    }

    @Transactional(readOnly=true)
    public Set<UUID> authorizedUrbanRuleIds(UUID caseId) {
        Map<UUID,Map<String,Object>> approved=new HashMap<>();
        for(var row:evidence(caseId)) approved.put((UUID)row.get("rule_id"),row);
        Set<UUID> ids=new LinkedHashSet<>();
        for(UrbanRuleEntity rule:rules.findAllByOrderByPriorityAscCodeAsc()) {
            if(!rule.isActive() || rule.getVerifiedKnowledgeRuleId()==null)continue;
            Map<String,Object> rv=approved.get(rule.getVerifiedKnowledgeRuleId());
            if(rv==null || !Objects.equals(rule.getRuleType().name(),rv.get("subject_code")))continue;
            String key=switch(rule.getRuleType()) {
                case MAX_FLOORS->"maxFloors";
                case MAX_FAR->"maxFar";
                case MAX_COVERAGE_PERCENT->"maxCoveragePercent";
                case MIN_PASSAGE_WIDTH->"minPassageWidthM";
                case MIN_FRONTAGE->"minFrontageM";
                case MAX_HEIGHT->"maxHeightM";
            };
            String expectedOp=rule.getRuleType().name().startsWith("MIN_")?">=":"<=";
            String unit=switch(rule.getRuleType()) {
                case MAX_FLOORS->"FLOOR";case MAX_FAR->"RATIO";
                case MAX_COVERAGE_PERCENT->"PERCENT";default->"M";
            };
            if(!expectedOp.equals(rv.get("verified_operator")) ||
               !unit.equalsIgnoreCase(Objects.toString(rv.get("unit_code"),"")) ||
               !matches(rule.getJurisdictionCountry(),(String)rv.get("scope_country")) ||
               (rule.getJurisdictionCountry()==null && rv.get("scope_country")!=null) ||
               !matches(rule.getJurisdictionCity(),(String)rv.get("scope_city")) ||
               (rule.getJurisdictionCity()==null && rv.get("scope_city")!=null) ||
               !matches(rule.getJurisdictionDistrict(),(String)rv.get("scope_district")) ||
               (rule.getJurisdictionDistrict()==null && rv.get("scope_district")!=null))continue;
            Object actual=rule.getParameters().get(key),expected=rv.get("numeric_value");
            try {
                if(actual==null || expected==null ||
                   new BigDecimal(actual.toString()).compareTo(new BigDecimal(expected.toString()))!=0) continue;
            } catch(RuntimeException ex) {continue;}
            ids.add(rule.getId());
        }
        return ids;
    }

    @Transactional
    public Map<String,Object> bind(UUID urbanRuleId,UUID knowledgeRuleId,String reviewer){
        return binding(urbanRuleId,knowledgeRuleId,reviewer,true);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> previewBind(UUID urbanRuleId,UUID knowledgeRuleId,String reviewer){
        return binding(urbanRuleId,knowledgeRuleId,reviewer,false);
    }

    private Map<String,Object> binding(UUID urbanRuleId,UUID knowledgeRuleId,String reviewer,boolean apply){
        requireRuleManager(reviewer);
        if(reviewer==null||reviewer.isBlank())throw new IllegalArgumentException("Reviewer required");
        UrbanRuleEntity urban=rules.findById(urbanRuleId).orElseThrow(()->new IllegalArgumentException("Urban rule not found"));
        var reviewed=jdbc.queryForList("""
            select k.id,k.numeric_value,v.verified_operator,v.subject_code,v.unit_code,v.scope_country,
                   v.scope_city,v.scope_district,v.valid_from,v.valid_to
            from knowledge_rule k join knowledge_rule_review v on v.rule_id=k.id
            join knowledge_source_document d on d.id=v.source_document_id
            join knowledge_release rel on rel.knowledge_version_id=k.knowledge_version_id
            where k.id=? and k.status='VERIFIED' and v.decision='APPROVED' and rel.status='PUBLISHED'
              and v.source_sha256=d.sha256 and v.source_page>0
              and (v.valid_from is null or v.valid_from <= current_date)
              and (v.valid_to is null or v.valid_to >= current_date)
            """,knowledgeRuleId);
        if(reviewed.isEmpty())throw new IllegalArgumentException("Knowledge rule must be approved and published");
        var source=reviewed.get(0);
        UrbanRuleType type=urban.getRuleType();
        if(!type.name().equals(source.get("subject_code")))throw new IllegalArgumentException("Rule type and reviewed subject mismatch");
        String expectedOp=type.name().startsWith("MIN_")?">=":"<=";
        if(!expectedOp.equals(source.get("verified_operator")))throw new IllegalArgumentException("Operator mismatch");
        if(!matches(urban.getJurisdictionCountry(),(String)source.get("scope_country")) ||
           (urban.getJurisdictionCountry()==null && source.get("scope_country")!=null))
            throw new IllegalArgumentException("Urban rule jurisdiction cannot exceed reviewed evidence");
        if(!matches(urban.getJurisdictionCity(),(String)source.get("scope_city")) ||
           !matches(urban.getJurisdictionDistrict(),(String)source.get("scope_district")) ||
           (urban.getJurisdictionCity()==null && source.get("scope_city")!=null) ||
           (urban.getJurisdictionDistrict()==null && source.get("scope_district")!=null))
            throw new IllegalArgumentException("Urban rule scope must not be broader than reviewed evidence");
        String key = switch(type){
            case MAX_FLOORS->"maxFloors";
            case MAX_FAR->"maxFar";
            case MAX_COVERAGE_PERCENT->"maxCoveragePercent";
            case MIN_PASSAGE_WIDTH->"minPassageWidthM";
            case MIN_FRONTAGE->"minFrontageM";
            case MAX_HEIGHT->"maxHeightM";
        };
        String unit = switch(type){
            case MAX_FLOORS->"FLOOR"; case MAX_FAR->"RATIO"; case MAX_COVERAGE_PERCENT->"PERCENT";
            default -> "M";
        };
        if(!unit.equalsIgnoreCase(Objects.toString(source.get("unit_code"),"")))throw new IllegalArgumentException("Unit mismatch");
        Object v=urban.getParameters().get(key);
        if(v==null || source.get("numeric_value")==null ||
           new BigDecimal(v.toString()).compareTo(new BigDecimal(source.get("numeric_value").toString()))!=0)
            throw new IllegalArgumentException("Urban rule numeric limit differs from verified source");
        if(apply) {
            // An explicit manager request, after independent review and publication.
            jdbc.update("update urban_rule set verified_knowledge_rule_id=? where id=?",knowledgeRuleId,urbanRuleId);
        }
        return Map.of("urbanRuleId",urbanRuleId,"knowledgeRuleId",knowledgeRuleId,
            "reviewer",reviewer,"status",apply?"BOUND":"READY_TO_BIND");
    }

    @Transactional(readOnly=true)
    public Map<String,Object> municipalityInputs(UUID caseId) {
        Set<UUID> allowed=authorizedUrbanRuleIds(caseId);
        PropertyEntity p=property(caseId);
        Map<String,Object> result=new LinkedHashMap<>();
        List<String> ruleIds=new ArrayList<>();
        Map<String,String> limits=new HashMap<>();
        Set<String> conflicted=new LinkedHashSet<>();
        for(UrbanRuleEntity r:rules.findAllByOrderByPriorityAscCodeAsc()){
            if(!allowed.contains(r.getId()))continue;
            if(!matches(r.getJurisdictionDistrict(),p.getDistrict()))continue;
            if(!matches(r.getJurisdictionCity(),p.getCity()))continue;
            if(!matches(r.getPropertyType(),p.getPropertyType()))continue;
            if(r.getValidFrom()!=null && r.getValidFrom().isAfter(LocalDate.now()))continue;
            if(r.getValidTo()!=null && r.getValidTo().isBefore(LocalDate.now()))continue;
            if(r.getRuleType()==UrbanRuleType.MAX_FLOORS) {
                Object v=r.getParameters().get("maxFloors");
                mergeBounded(result,limits,conflicted,"allowedResidentialFloors",v);
            }
            if(r.getRuleType()==UrbanRuleType.MAX_COVERAGE_PERCENT){
                Object v=r.getParameters().get("maxCoveragePercent");
                if(v!=null) mergeBounded(result,limits,conflicted,"coverageRatio",
                    new BigDecimal(v.toString()).divide(new BigDecimal("100")));
            }
            ruleIds.add(r.getId().toString());
        }
        for(String key:conflicted)result.remove(key);
        result.put("conflictingFields",List.copyOf(conflicted));
        result.put("reviewRequired",true); // Legal coverage is not certified in Phase B.
        result.put("verifiedUrbanRuleIds",ruleIds);
        result.put("source","APPROVED_PUBLISHED_KNOWLEDGE");
        return result;
    }
    private static void mergeBounded(Map<String,Object> result,Map<String,String> limits,
            Set<String> conflicts,String key,Object value) {
        if(value==null){conflicts.add(key);return;}
        String current=value.toString();
        String prior=limits.putIfAbsent(key,current);
        try {
            if(prior!=null && new BigDecimal(prior).compareTo(new BigDecimal(current))!=0)
                conflicts.add(key);
            else if(!conflicts.contains(key))result.put(key,value);
        } catch (RuntimeException ex){conflicts.add(key);}
    }
}
