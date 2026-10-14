package com.sakhtyar.regulation.application;

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
    public RegulationEvidenceService(JdbcTemplate jdbc,PropertyRepository properties,UrbanRuleRepository rules){
        this.jdbc=jdbc;this.properties=properties;this.rules=rules;
    }
    private static boolean matches(String scope,String value){return scope==null||scope.isBlank()||(value!=null&&scope.trim().equalsIgnoreCase(value.trim()));}
    private static LocalDate asDate(Object raw){
        if(raw==null)return null;
        if(raw instanceof LocalDate d)return d;
        if(raw instanceof java.sql.Date d)return d.toLocalDate();
        return LocalDate.parse(raw.toString());
    }
    private PropertyEntity property(UUID caseId){return properties.findByCaseId(caseId).orElseThrow(()->new IllegalArgumentException("Property not found for case"));}

    @Transactional(readOnly=true)
    public List<Map<String,Object>> evidence(UUID caseId) {
        PropertyEntity p=property(caseId);
        LocalDate now=LocalDate.now();
        List<Map<String,Object>> rows=jdbc.queryForList("""
             select r.id as rule_id,r.stable_key,r.numeric_value,r.operator,rv.subject_code,rv.unit_code,
               rv.scope_country,rv.scope_city,rv.scope_district,rv.source_authority,
               rv.source_sha256,rv.source_page,rv.valid_from,rv.valid_to,rv.reviewer,
               coalesce(nullif(d.filename,''),d.canonical_path) as original_name,rel.id as release_id,rel.release_key
             from knowledge_rule r join knowledge_rule_review rv on rv.rule_id=r.id and rv.decision='APPROVED'
             join knowledge_release rel on rel.knowledge_version_id=r.knowledge_version_id and rel.status='PUBLISHED'
             join knowledge_source_document d on d.id=rv.source_document_id
             where r.status='VERIFIED' and rv.source_page>0 and rv.source_sha256=d.sha256
             order by rel.published_at desc,r.id limit 1000
             """);
        return rows.stream().filter(x->matches((String)x.get("scope_country"),"IR"))
            .filter(x->matches((String)x.get("scope_city"),p.getCity()))
            .filter(x->matches((String)x.get("scope_district"),p.getDistrict()))
            .filter(x->{LocalDate from=asDate(x.get("valid_from")),to=asDate(x.get("valid_to"));return (from==null||!now.isBefore(from))&&(to==null||!now.isAfter(to));})
            .toList();
    }

    @Transactional(readOnly=true)
    public Set<UUID> authorizedUrbanRuleIds(UUID caseId) {
        Set<UUID> approved = new HashSet<>();
        for(var row:evidence(caseId)) approved.add((UUID)row.get("rule_id"));
        Set<UUID> ids = new HashSet<>();
        for (Map<String,Object> row:jdbc.queryForList("select id,verified_knowledge_rule_id from urban_rule where active=true and verified_knowledge_rule_id is not null")) {
            if (approved.contains(row.get("verified_knowledge_rule_id"))) ids.add((UUID)row.get("id"));
        }
        return ids;
    }

    @Transactional
    public Map<String,Object> bind(UUID urbanRuleId,UUID knowledgeRuleId,String reviewer){
        if(reviewer==null||reviewer.isBlank())throw new IllegalArgumentException("Reviewer required");
        UrbanRuleEntity urban=rules.findById(urbanRuleId).orElseThrow(()->new IllegalArgumentException("Urban rule not found"));
        var reviewed=jdbc.queryForList("""
            select k.id,k.numeric_value,v.verified_operator,v.subject_code,v.unit_code,v.scope_country,
                   v.scope_city,v.scope_district,v.valid_from,v.valid_to
            from knowledge_rule k join knowledge_rule_review v on v.rule_id=k.id
            join knowledge_release rel on rel.knowledge_version_id=k.knowledge_version_id
            where k.id=? and k.status='VERIFIED' and v.decision='APPROVED' and rel.status='PUBLISHED'
            """,knowledgeRuleId);
        if(reviewed.isEmpty())throw new IllegalArgumentException("Knowledge rule must be approved and published");
        var source=reviewed.get(0);
        UrbanRuleType type=urban.getRuleType();
        if(!type.name().equals(source.get("subject_code")))throw new IllegalArgumentException("Rule type and reviewed subject mismatch");
        String expectedOp=type.name().startsWith("MIN_")?">=":"<=";
        if(!expectedOp.equals(source.get("verified_operator")))throw new IllegalArgumentException("Operator mismatch");
        if(!"IR".equals(source.get("scope_country")))throw new IllegalArgumentException("Expected Iran jurisdiction");
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
        if(v==null || new BigDecimal(v.toString()).compareTo((BigDecimal)source.get("numeric_value"))!=0)
            throw new IllegalArgumentException("Urban rule numeric limit differs from verified source");
        jdbc.update("update urban_rule set verified_knowledge_rule_id=? where id=?",knowledgeRuleId,urbanRuleId);
        return Map.of("urbanRuleId",urbanRuleId,"knowledgeRuleId",knowledgeRuleId,"reviewer",reviewer,"status","BOUND");
    }

    @Transactional(readOnly=true)
    public Map<String,Object> municipalityInputs(UUID caseId) {
        Set<UUID> allowed=authorizedUrbanRuleIds(caseId);
        PropertyEntity p=property(caseId);
        Map<String,Object> result=new LinkedHashMap<>();
        List<String> ruleIds=new ArrayList<>();
        for(UrbanRuleEntity r:rules.findAllByOrderByPriorityAscCodeAsc()){
            if(!allowed.contains(r.getId()))continue;
            if(!matches(r.getJurisdictionDistrict(),p.getDistrict()))continue;
            if(!matches(r.getJurisdictionCity(),p.getCity()))continue;
            if(!matches(r.getPropertyType(),p.getPropertyType()))continue;
            if(r.getValidFrom()!=null && r.getValidFrom().isAfter(LocalDate.now()))continue;
            if(r.getValidTo()!=null && r.getValidTo().isBefore(LocalDate.now()))continue;
            if(r.getRuleType()==UrbanRuleType.MAX_FLOORS)result.putIfAbsent("allowedResidentialFloors",r.getParameters().get("maxFloors"));
            if(r.getRuleType()==UrbanRuleType.MAX_COVERAGE_PERCENT){
                Object v=r.getParameters().get("maxCoveragePercent");
                if(v!=null)result.putIfAbsent("coverageRatio",new BigDecimal(v.toString()).divide(new BigDecimal("100")));
            }
            ruleIds.add(r.getId().toString());
        }
        result.put("verifiedUrbanRuleIds",ruleIds);
        result.put("source","APPROVED_PUBLISHED_KNOWLEDGE");
        return result;
    }
}
