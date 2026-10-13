package com.sakhtyar.knowledge.governance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.*;

/** Phase 5.18: supervised classification and traceable knowledge review workbench. */
@RestController
@RequestMapping("/api/v1/knowledge/admin/catalog")
public class KnowledgeCatalogController {
    private static final Set<String> CATEGORIES = Set.of("LAW", "EDUCATION", "TECHNICAL", "REFERENCE", "OTHER");
    private final JdbcTemplate jdbc;

    public KnowledgeCatalogController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static String requireReviewer(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getAuthorities().stream()
                .noneMatch(a -> "USER_MANAGE".equals(a.getAuthority()))) {
            throw new AccessDeniedException("USER_MANAGE authority required");
        }
        return auth.getName();
    }
    private static int bound(int value, int min, int max) {return Math.max(min, Math.min(max, value));}
    private static String pattern(String s) {
        return s == null ? "" : s.strip();
    }

    // LIST endpoints must remain readable even before V47 is applied.
    // Classification writes remain blocked until Flyway installs the schema.
    private boolean classificationReady() {
        return jdbc.queryForObject(
                "select to_regclass('knowledge_document_classification') is not null", Boolean.class);
    }
    private String classificationJoin(boolean ready) {
        return ready
                ? " left join knowledge_document_classification c on c.document_id=d.id "
                : " left join lateral (select null::varchar(30) category, null::text notes, "
                  + "null::varchar(160) reviewed_by, null::timestamptz reviewed_at) c on true ";
    }

    @GetMapping("/health")
    @Transactional(readOnly = true)
    public Map<String,Object> health(Authentication auth) {
        requireReviewer(auth);
        boolean ready = classificationReady();
        long documents = jdbc.queryForObject("select count(*) from knowledge_source_document", Long.class);
        long rules = jdbc.queryForObject("select count(*) from knowledge_rule", Long.class);
        long inventories = jdbc.queryForObject("select count(*) from knowledge_intake_inventory", Long.class);
        return Map.of("classificationReady",ready,"documents",documents,"rules",rules,
                "inventoryRecords",inventories,"migration","V47");
    }

    @GetMapping("/documents")
    @Transactional(readOnly = true)
    public Map<String,Object> documents(@RequestParam(defaultValue="1") int page,
                                        @RequestParam(defaultValue="20") int pageSize,
                                        @RequestParam(defaultValue="") String search,
                                        @RequestParam(defaultValue="ALL") String category,
                                        Authentication auth) {
        requireReviewer(auth);
        int p=bound(page,1,100000), n=bound(pageSize,1,100);
        boolean ready = classificationReady();
        String filter = """
            from knowledge_source_document d
            join lateral (
                select relative_path,detected_title,detected_category,page_count,intake_status,execution_id
                from knowledge_intake_inventory i where i.document_id=d.id
                order by i.updated_at desc limit 1
            ) i on true
            """ + classificationJoin(ready) + """
            where (position(lower(?) in lower(coalesce(d.original_name,''))) > 0
               or position(lower(?) in lower(coalesce(i.relative_path,''))) > 0)
              and (?='ALL' or coalesce(c.category,'UNCLASSIFIED')=?)
            """;
        Object[] args={pattern(search),pattern(search),category,category};
        long total=jdbc.queryForObject("select count(*) "+filter, Long.class, args);
        List<Map<String,Object>> items=jdbc.queryForList("""
            select d.id::text as id,d.original_name,d.sha256,i.relative_path,i.detected_title,
                   i.detected_category as folder_category,i.page_count,i.intake_status,
                   i.execution_id::text as execution_id,
                   coalesce(c.category,'UNCLASSIFIED') as category,c.notes,c.reviewed_by,c.reviewed_at,
                   (select count(*) from knowledge_rule r
                      join knowledge_source_node sn on sn.id=r.source_node_id
                      join knowledge_source_artifact a on a.id=sn.artifact_id
                     where a.document_id=d.id) as extracted_rule_count
            """+filter+" order by d.original_name,d.id limit ? offset ?", concat(args,n,(p-1)*n));
        return Map.of("items",items,"total",total,"page",p,"pageSize",n, "classificationReady",ready);
    }

    @GetMapping("/rules")
    @Transactional(readOnly = true)
    public Map<String,Object> rules(@RequestParam(defaultValue="1") int page,
                                    @RequestParam(defaultValue="20") int pageSize,
                                    @RequestParam(defaultValue="") String search,
                                    @RequestParam(defaultValue="ALL") String category,
                                    @RequestParam(defaultValue="ALL") String status,
                                    @RequestParam(required=false) UUID executionId,
                                    Authentication auth) {
        requireReviewer(auth);
        int p=bound(page,1,100000),n=bound(pageSize,1,100);
        boolean ready = classificationReady();
        String filter = """
            from knowledge_rule r
            join knowledge_source_node sn on sn.id=r.source_node_id
            join knowledge_source_artifact a on a.id=sn.artifact_id
            join knowledge_source_document d on d.id=a.document_id
            left join knowledge_rule_review v on v.rule_id=r.id
            """ + classificationJoin(ready) + """
            where (position(lower(?) in lower(coalesce(r.stable_key,''))) > 0
               or position(lower(?) in lower(coalesce(r.text_value,''))) > 0
               or position(lower(?) in lower(coalesce(d.original_name,''))) > 0)
              and (?='ALL' or coalesce(c.category,'UNCLASSIFIED')=?)
              and (?='ALL' or r.status=?)
              and (?::uuid is null or a.execution_id=?::uuid)
            """;
        Object[] args={pattern(search),pattern(search),pattern(search),category,category,status,status,
                executionId,executionId};
        long total=jdbc.queryForObject("select count(*) "+filter,Long.class,args);
        List<Map<String,Object>> items=jdbc.queryForList("""
            select r.id::text as id,r.stable_key,r.operator,r.numeric_value,r.text_value,
                   r.confidence,r.status,r.jurisdiction_code,r.valid_from,r.valid_to,
                   sn.page_from,d.original_name,d.id::text as document_id,d.sha256,
                   a.execution_id::text as execution_id,
                   coalesce(c.category,'UNCLASSIFIED') as category,
                   v.decision,v.reviewer,v.reviewed_at,v.subject_code,v.unit_code,
                   v.scope_city,v.source_authority
            """+filter+" order by d.original_name,sn.page_from,r.stable_key,r.id limit ? offset ?",
                concat(args,n,(p-1)*n));
        return Map.of("items",items,"total",total,"page",p,"pageSize",n, "classificationReady",ready);
    }

    public record ClassifyRequest(String category,String notes) {}

    @PostMapping("/documents/{id}/classification")
    @Transactional
    public Map<String,Object> classify(@PathVariable UUID id,@RequestBody ClassifyRequest request,Authentication auth) {
        String reviewer=requireReviewer(auth);
        if(!classificationReady())
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Flyway V47 is not applied: restart backend after confirming no active knowledge processing");
        if(request==null||request.category()==null||!CATEGORIES.contains(request.category()))
            throw new IllegalArgumentException("Choose LAW, EDUCATION, TECHNICAL, REFERENCE or OTHER");
        String notes=request.notes()==null?"":request.notes().strip();
        if(notes.length()>2000)throw new IllegalArgumentException("Classification notes too long");
        if("LAW".equals(request.category()) && notes.length()<12)
            throw new IllegalArgumentException("Legal classification requires a written rationale (at least 12 characters)");
        Integer exists=jdbc.queryForObject("select count(*) from knowledge_source_document where id=?",Integer.class,id);
        if(exists==null||exists==0) throw new IllegalArgumentException("Document not found");
        if(!"LAW".equals(request.category())) {
            Integer approvedRules=jdbc.queryForObject("""
                select count(*) from knowledge_rule r
                join knowledge_source_node n on n.id=r.source_node_id
                join knowledge_source_artifact a on a.id=n.artifact_id
                where a.document_id=? and r.status='VERIFIED'
                """, Integer.class, id);
            if(approvedRules!=null && approvedRules>0)
                throw new IllegalStateException("Cannot remove LAW classification while verified rules reference this document");
        }
        jdbc.update("""
            insert into knowledge_document_classification(document_id,category,notes,reviewed_by,reviewed_at)
            values(?,?,?,?,now())
            on conflict(document_id) do update set category=excluded.category,notes=excluded.notes,
            reviewed_by=excluded.reviewed_by,reviewed_at=now()
            """,id,request.category(),notes,reviewer);
        jdbc.update("""
            insert into knowledge_document_classification_event(document_id,category,notes,reviewed_by)
            values(?,?,?,?)
            """,id,request.category(),notes,reviewer);
        return Map.of("id",id,"category",request.category(),"reviewedBy",reviewer);
    }

    private static Object[] concat(Object[] base,Object... extra) {
        Object[] result=Arrays.copyOf(base,base.length+extra.length);
        System.arraycopy(extra,0,result,base.length,extra.length);
        return result;
    }
}
