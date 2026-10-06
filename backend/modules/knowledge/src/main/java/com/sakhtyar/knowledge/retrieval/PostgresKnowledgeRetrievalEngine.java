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
        int safeLimit=Math.max(1,Math.min(limit,100));
        Map<String,Object> release=jdbc.queryForMap(
            "select id,knowledge_version_id,graph_version_id,retrieval_version from knowledge_release where id=? and status='PUBLISHED'",releaseId);
        UUID knowledge=(UUID)release.get("knowledge_version_id");
        UUID graph=(UUID)release.get("graph_version_id");
        List<Map<String,Object>> rules=jdbc.queryForList(
            "select r.id,r.stable_key,r.operator,r.numeric_value,r.text_value,r.confidence,n.page_from,n.content from knowledge_rule r left join knowledge_source_node n on n.id=r.source_node_id where r.knowledge_version_id=? and (coalesce(r.text_value,'') ilike ? or coalesce(n.content,'') ilike ?) order by r.confidence desc limit ?",
            knowledge,"%"+query+"%","%"+query+"%",safeLimit);
        List<Map<String,Object>> graphRows=jdbc.queryForList(
            "select e.relation_type,fn.stable_key from knowledge_graph_edge e join knowledge_graph_node fn on fn.id=e.from_node_id where e.graph_version_id=? limit ?",
            graph,safeLimit);
        return new EvidencePack(releaseId,query,Objects.toString(release.get("retrieval_version"),"postgres-fts-v1"),rules,rules,graphRows,List.of());
    }
}