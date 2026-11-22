package com.sakhtyar.knowledge.retrieval;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class PostgresKnowledgeRetrievalEngine implements KnowledgeRetrievalEngine {
    private final JdbcTemplate jdbc;
    public PostgresKnowledgeRetrievalEngine(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override
    public EvidencePack retrieve(UUID releaseId,String query,int limit){
        Map<String,Object> rel=jdbc.queryForMap("select id,knowledge_version_id,graph_version_id,retrieval_version from knowledge_release where id=? and status='PUBLISHED'",releaseId);
        UUID version=(UUID)rel.get("knowledge_version_id"), graph=(UUID)rel.get("graph_version_id");
        int capped=Math.max(1,Math.min(limit,100));
        String q="%"+Objects.toString(query,"").replace("%","\\%").replace("_","\\_")+"%";
        List<Map<String,Object>> rules=jdbc.queryForList("""
            select r.id,r.stable_key,r.operator,r.numeric_value,r.text_value,r.confidence,
                   n.page_from,n.content,rv.source_authority,rv.source_page,rv.source_sha256,
                   rv.scope_city,rv.scope_district,coalesce(nullif(d.filename,''),d.canonical_path) as original_name
              from knowledge_rule r
              join knowledge_rule_review rv on rv.rule_id=r.id and rv.decision='APPROVED'
              join knowledge_source_node n on n.id=r.source_node_id
              join knowledge_source_document d on d.id=rv.source_document_id and d.sha256=rv.source_sha256
             where r.knowledge_version_id=? and r.status='VERIFIED'
               and rv.valid_from<=current_date and (rv.valid_to is null or rv.valid_to>=current_date)
               and (coalesce(r.text_value,'') ilike ? or coalesce(n.content,'') ilike ?)
             order by r.confidence desc limit ?
             """,version,q,q,capped);
        // Graph results only derive from approved rule nodes; no unverified graph context is returned.
        List<Map<String,Object>> graphRows=jdbc.queryForList("""
             select g.relation_type, n.stable_key
               from knowledge_graph_edge g
               join knowledge_graph_node n on n.id=g.from_node_id
               join knowledge_rule r on r.id=n.entity_id
               join knowledge_rule_review rv on rv.rule_id=r.id and rv.decision='APPROVED'
              where g.graph_version_id=? and r.status='VERIFIED' limit ?
             """,graph,capped);
        return new EvidencePack(releaseId,query,Objects.toString(rel.get("retrieval_version"),"postgres-reviewed-v1"),rules,rules,graphRows,List.of());
    }
}
