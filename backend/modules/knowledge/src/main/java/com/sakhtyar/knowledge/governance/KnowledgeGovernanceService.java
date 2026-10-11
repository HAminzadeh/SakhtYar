package com.sakhtyar.knowledge.governance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service
public class KnowledgeGovernanceService {
    private final JdbcTemplate jdbc;
    public KnowledgeGovernanceService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record ReviewRequest(String decision, String subjectCode, String operator, String unitCode,
                                String country, String city, String district, String sourceAuthority,
                                LocalDate validFrom, LocalDate validTo, String notes) { }

    public List<Map<String,Object>> pending(int limit) {
        return jdbc.queryForList("""
            select r.id,r.stable_key,r.numeric_value,r.text_value,r.status,n.page_from,
                   d.original_name,d.sha256
              from knowledge_rule r join knowledge_source_node n on n.id=r.source_node_id
              join knowledge_source_artifact a on a.id=n.artifact_id
              join knowledge_source_document d on d.id=a.document_id
              left join knowledge_rule_review v on v.rule_id=r.id
             where v.rule_id is null and r.status='CANDIDATE'
             order by r.id limit ?
            """, Math.max(1,Math.min(limit,200)));
    }

    @Transactional
    public Map<String,Object> review(UUID id, ReviewRequest req, String reviewer) {
        if (reviewer == null || reviewer.isBlank()) throw new IllegalArgumentException("Authenticated reviewer required");
        if (req == null || !Set.of("APPROVED", "REJECTED").contains(req.decision()))
            throw new IllegalArgumentException("Decision must be APPROVED or REJECTED");
        List<Map<String,Object>> rows=jdbc.queryForList("""
            select r.id, r.knowledge_version_id, r.numeric_value, r.status, n.page_from,
                   d.id as document_id, d.sha256
              from knowledge_rule r
              join knowledge_source_node n on n.id=r.source_node_id
              join knowledge_source_artifact a on a.id=n.artifact_id
              join knowledge_source_document d on d.id=a.document_id
             where r.id=? for update of r
            """,id);
        if(rows.isEmpty()) throw new IllegalArgumentException("Rule lacks traceable document and page");
        Map<String,Object> row=rows.get(0);
        if(!"CANDIDATE".equals(row.get("status")) && !"VERIFIED".equals(row.get("status")) && !"REJECTED".equals(row.get("status")))
            throw new IllegalStateException("Rule cannot be reviewed in current state");
        Integer releases=jdbc.queryForObject("select count(*) from knowledge_release where knowledge_version_id=? and status='PUBLISHED'",Integer.class,row.get("knowledge_version_id"));
        if(releases != null && releases>0) throw new IllegalStateException("Published release is immutable");
        if("APPROVED".equals(req.decision())) {
            require(req.subjectCode(),"subjectCode"); require(req.unitCode(),"unitCode");
            require(req.country(),"country"); require(req.city(),"city"); require(req.sourceAuthority(),"sourceAuthority");
            if(req.validFrom()==null) throw new IllegalArgumentException("Effective date required");
            if(req.validTo()!=null && req.validTo().isBefore(req.validFrom())) throw new IllegalArgumentException("Invalid effective date range");
            if(req.operator()==null || !Set.of("<=",">=","=","<",">").contains(req.operator())) throw new IllegalArgumentException("Explicit semantic operator required");
            if(row.get("numeric_value")==null) throw new IllegalArgumentException("Numeric rule value missing");
        }
        Integer page=((Number)row.get("page_from")).intValue();
        if(page<1) throw new IllegalArgumentException("Invalid source page");
        String country=req.country()==null?null:req.country().trim().toUpperCase(Locale.ROOT);
        if("APPROVED".equals(req.decision()) && country.length()!=2) throw new IllegalArgumentException("ISO country code required");
        jdbc.update("""
           insert into knowledge_rule_review(rule_id,decision,subject_code,verified_operator,unit_code,
              scope_country,scope_city,scope_district,source_authority,source_document_id,source_sha256,
              source_page,valid_from,valid_to,reviewer,reviewed_at,notes)
           values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,now(),?)
           on conflict(rule_id) do update set decision=excluded.decision, subject_code=excluded.subject_code,
             verified_operator=excluded.verified_operator,unit_code=excluded.unit_code,
             scope_country=excluded.scope_country,scope_city=excluded.scope_city,scope_district=excluded.scope_district,
             source_authority=excluded.source_authority,source_document_id=excluded.source_document_id,
             source_sha256=excluded.source_sha256,source_page=excluded.source_page,
             valid_from=excluded.valid_from,valid_to=excluded.valid_to,reviewer=excluded.reviewer,
             reviewed_at=now(),notes=excluded.notes
           """,id,req.decision(),req.subjectCode(),req.operator(),req.unitCode(),country,
            trim(req.city()),trim(req.district()),req.sourceAuthority(),row.get("document_id"),row.get("sha256"),
            page,req.validFrom(),req.validTo(),reviewer,Objects.toString(req.notes(),""));
        jdbc.update("update knowledge_rule set status=?,operator=?,jurisdiction_code=?,valid_from=?,valid_to=? where id=?",
            "APPROVED".equals(req.decision())?"VERIFIED":"REJECTED",
            "APPROVED".equals(req.decision())?req.operator():"OBSERVED",
            "APPROVED".equals(req.decision())?country+":"+trim(req.city())+":"+trim(req.district()):null,
            req.validFrom(),req.validTo(),id);
        jdbc.update("""
          insert into knowledge_rule_review_event(rule_id,decision,reviewed_by,review_snapshot)
          select rule_id,decision,reviewer,to_jsonb(v) from knowledge_rule_review v where rule_id=?
          """,id);
        return Map.of("ruleId",id,"decision",req.decision(),"sourcePage",page,"reviewer",reviewer);
    }

    @Transactional
    public Map<String,Object> publish(UUID releaseId, String reviewer) {
        if(reviewer==null||reviewer.isBlank())throw new IllegalArgumentException("Authenticated reviewer required");
        var releases=jdbc.queryForList("select id,knowledge_version_id,status from knowledge_release where id=? for update",releaseId);
        if(releases.isEmpty())throw new IllegalArgumentException("Release not found");
        var release=releases.get(0);
        if(!"DRAFT".equals(release.get("status")))throw new IllegalStateException("Release must be DRAFT");
        UUID version=(UUID)release.get("knowledge_version_id");
        Integer pending=jdbc.queryForObject("""
           select count(*) from knowledge_rule r left join knowledge_rule_review v on v.rule_id=r.id
           where r.knowledge_version_id=? and (v.rule_id is null or
             (v.decision='APPROVED' and (r.status<>'VERIFIED' or r.source_node_id is null)))
           """,Integer.class,version);
        Integer approved=jdbc.queryForObject("""
           select count(*) from knowledge_rule r join knowledge_rule_review v on v.rule_id=r.id
           where r.knowledge_version_id=? and v.decision='APPROVED' and r.status='VERIFIED'
           """,Integer.class,version);
        if(pending==null || pending>0 || approved==null || approved<1)
            throw new IllegalStateException("Cannot publish: all candidate rules require individual review and at least one approved rule");
        // The graph was prepared when these rules were only candidates. Add reviewed edges now.
        UUID graph=(UUID)jdbc.queryForMap("select graph_version_id from knowledge_release where id=?",releaseId).get("graph_version_id");
        jdbc.update("""
            insert into knowledge_graph_node(id,graph_version_id,node_type,entity_type,entity_id,stable_key)
            select gen_random_uuid(),?,'RULE','KNOWLEDGE_RULE',r.id,r.stable_key
              from knowledge_rule r join knowledge_rule_review v on v.rule_id=r.id and v.decision='APPROVED'
             where r.knowledge_version_id=? and r.status='VERIFIED'
               and not exists (select 1 from knowledge_graph_node g where g.graph_version_id=? and g.entity_id=r.id)
            """,graph,version,graph);
        jdbc.update("""
            insert into knowledge_graph_edge(id,graph_version_id,from_node_id,relation_type,to_node_id,confidence,source_node_id)
            select gen_random_uuid(),?,gn.id,'SUPPORTED_BY',doc.id,1,r.source_node_id
              from knowledge_rule r join knowledge_rule_review v on v.rule_id=r.id and v.decision='APPROVED'
              join knowledge_graph_node gn on gn.graph_version_id=? and gn.entity_id=r.id
              join knowledge_graph_node doc on doc.graph_version_id=? and doc.entity_type='SOURCE_DOCUMENT' and doc.entity_id=v.source_document_id
             where r.knowledge_version_id=? and not exists (select 1 from knowledge_graph_edge e where e.graph_version_id=? and e.from_node_id=gn.id and e.to_node_id=doc.id)
            """,graph,graph,graph,version,graph);
        // No published data is overwritten. The exact reviewed version becomes immutable.
        jdbc.update("update knowledge_release set status='PUBLISHED',published_at=now(),notes=concat(coalesce(notes,''),' | Approved by ',?) where id=?",reviewer,releaseId);
        jdbc.update("update knowledge_release_component set compatibility_status='COMPATIBLE' where release_id=?",releaseId);
        jdbc.update("update structured_knowledge_version set status='PUBLISHED',published_at=now() where id=?",version);
        jdbc.update("update construction_catalog_version set status='PUBLISHED',published_at=now() where id=(select catalog_version_id from knowledge_release where id=?)",releaseId);
        jdbc.update("update knowledge_graph_version set status='PUBLISHED' where id=(select graph_version_id from knowledge_release where id=?)",releaseId);
        jdbc.update("update knowledge_graphrag_version set status='PUBLISHED' where id=(select graphrag_version_id from knowledge_release where id=?)",releaseId);
        return Map.of("releaseId",releaseId,"status","PUBLISHED","approvedRuleCount",approved);
    }

    public List<Map<String,Object>> releases() {
        return jdbc.queryForList("""
          select r.id,r.release_key,r.status,r.created_at,r.published_at,
          (select count(*) from knowledge_rule k where k.knowledge_version_id=r.knowledge_version_id) rules,
          (select count(*) from knowledge_rule k join knowledge_rule_review v on v.rule_id=k.id where k.knowledge_version_id=r.knowledge_version_id and v.decision='APPROVED') approved
          from knowledge_release r order by r.created_at desc limit 50
          """);
    }
    private static void require(String s,String name){if(s==null||s.isBlank())throw new IllegalArgumentException(name+" is required");}
    private static String trim(String s){return s==null?null:s.trim();}
}
