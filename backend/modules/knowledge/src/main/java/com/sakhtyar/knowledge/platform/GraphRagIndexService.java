package com.sakhtyar.knowledge.platform;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service; import java.util.*;
@Service public class GraphRagIndexService {
 private final JdbcTemplate jdbc; public GraphRagIndexService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<Map<String,Object>> neighborhood(UUID graphVersionId, UUID nodeId){ return jdbc.queryForList("select e.relation_type,e.confidence,n2.entity_type,n2.entity_id,n2.stable_key from knowledge_graph_edge e join knowledge_graph_node n2 on n2.id=e.to_node_id where e.graph_version_id=? and e.from_node_id=? union all select e.relation_type,e.confidence,n1.entity_type,n1.entity_id,n1.stable_key from knowledge_graph_edge e join knowledge_graph_node n1 on n1.id=e.from_node_id where e.graph_version_id=? and e.to_node_id=? limit 200",graphVersionId,nodeId,graphVersionId,nodeId); }
 public List<Map<String,Object>> communities(UUID graphRagVersionId){ return jdbc.queryForList("select id,parent_community_id,level,stable_key,title,summary,member_count from knowledge_graph_community where graphrag_version_id=? order by level,member_count desc",graphRagVersionId); }
}