package com.sakhtyar.knowledge.platform;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service; import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.util.*;
@Service public class KnowledgeReleaseService {
 private final JdbcTemplate jdbc; public KnowledgeReleaseService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public Map<String,Object> compatibility(UUID releaseId){ var rows=jdbc.queryForList("select component_type,component_version,content_hash,dependency_hash,compatibility_status from knowledge_release_component where release_id=? order by component_type",releaseId); boolean ok=rows.stream().allMatch(r->"COMPATIBLE".equals(r.get("compatibility_status"))); return Map.of("releaseId",releaseId,"compatible",ok,"components",rows); }
 public String hash(String... parts){ try{MessageDigest d=MessageDigest.getInstance("SHA-256"); for(String p:parts)d.update((p==null?"":p).getBytes(StandardCharsets.UTF_8)); return HexFormat.of().formatHex(d.digest());}catch(Exception e){throw new IllegalStateException(e);} }
}