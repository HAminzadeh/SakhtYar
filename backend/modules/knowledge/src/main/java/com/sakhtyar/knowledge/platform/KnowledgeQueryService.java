package com.sakhtyar.knowledge.platform;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class KnowledgeQueryService {
    private final JdbcTemplate jdbc;
    public KnowledgeQueryService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<Map<String,Object>> rules(UUID releaseId,UUID conceptId,String jurisdiction){
        return jdbc.queryForList("""
           select r.*, n.content as evidence_text, rv.source_page, rv.source_authority,
                  rv.source_sha256, rv.scope_city, rv.scope_district, d.original_name
             from knowledge_release rel
             join knowledge_rule r on r.knowledge_version_id=rel.knowledge_version_id
             join knowledge_rule_review rv on rv.rule_id=r.id and rv.decision='APPROVED'
             join knowledge_source_node n on n.id=r.source_node_id
             join knowledge_source_document d on d.id=rv.source_document_id and d.sha256=rv.source_sha256
            where rel.id=? and rel.status='PUBLISHED' and r.subject_concept_id=?
              and (? is null or r.jurisdiction_code=?) and r.status='VERIFIED'
              and rv.valid_from<=current_date and (rv.valid_to is null or rv.valid_to>=current_date)
            order by r.confidence desc limit 200
           """,releaseId,conceptId,jurisdiction,jurisdiction);
    }
}
