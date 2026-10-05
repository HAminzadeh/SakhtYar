package com.sakhtyar.knowledge.platform;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service; import java.util.*;
@Service public class KnowledgeQueryService {
 private final JdbcTemplate jdbc; public KnowledgeQueryService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<Map<String,Object>> rules(UUID releaseId, UUID conceptId, String jurisdiction){ return jdbc.queryForList("select r.*,n.content as evidence_text,n.page_from,n.page_to from knowledge_release rel join knowledge_rule r on r.knowledge_version_id=rel.knowledge_version_id left join knowledge_source_node n on n.id=r.source_node_id where rel.id=? and r.subject_concept_id=? and (? is null or r.jurisdiction_code=?) and r.status in ('VERIFIED','PUBLISHED') order by r.confidence desc", releaseId,conceptId,jurisdiction,jurisdiction); }
}