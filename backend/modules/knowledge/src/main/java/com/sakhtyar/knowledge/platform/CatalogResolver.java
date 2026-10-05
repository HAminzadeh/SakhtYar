package com.sakhtyar.knowledge.platform;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service; import java.util.*;
@Service public class CatalogResolver {
 private final JdbcTemplate jdbc; public CatalogResolver(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<Map<String,Object>> resolve(String normalizedTerm){ return jdbc.queryForList("select c.id,c.stable_key,c.canonical_name_fa,a.alias,a.confidence from construction_concept_alias a join construction_concept c on c.id=a.concept_id join construction_catalog_version v on v.id=c.catalog_version_id where v.status='PUBLISHED' and a.normalized_alias=? order by a.confidence desc nulls last limit 20", normalizedTerm); }
}