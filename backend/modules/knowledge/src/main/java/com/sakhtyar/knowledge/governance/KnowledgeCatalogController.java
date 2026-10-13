package com.sakhtyar.knowledge.governance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.*;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Phase 5.18: supervised classification and traceable knowledge review workbench. */
@RestController
@RequestMapping("/api/v1/knowledge/admin/catalog")
public class KnowledgeCatalogController {
    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeCatalogController.class);
    private static final String API_REVISION = "5.18.3-catalog-paginated-safe-sql";
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

    // Bind only filters actually requested. The old query used untyped/all-mode
    // parameters, a nullable UUID bind, and a mandatory LATERAL inventory join.
    // Those combinations are fragile across PostgreSQL/JDBC schema generations.
    private static String like(String s) { return "%" + pattern(s) + "%"; }

    private static Map<String,Object> result(List<Map<String,Object>> rows,long total,int page,int size,boolean ready) {
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("items",rows);
        out.put("total",total);
        out.put("page",page);
        out.put("pageSize",size);
        out.put("classificationReady",ready);
        out.put("apiRevision",API_REVISION);
        return out;
    }

    private String originalNameExpression() {
        Boolean found=jdbc.queryForObject("""
            select exists(select 1 from information_schema.columns
             where table_schema=current_schema() and table_name='knowledge_source_document'
               and column_name='original_name')
            """, Boolean.class);
        return Boolean.TRUE.equals(found) ? "d.original_name" : "d.filename";
    }

    @GetMapping("/diagnostics")
    @Transactional(readOnly = true)
    public Map<String,Object> diagnostics(Authentication auth) {
        requireReviewer(auth);
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("apiRevision",API_REVISION);
        out.put("classificationReady",classificationReady());
        for (String name : List.of("knowledge_source_document","knowledge_intake_inventory",
                "knowledge_rule","knowledge_source_node","knowledge_source_artifact",
                "knowledge_rule_review","knowledge_document_classification")) {
            try {
                out.put(name,jdbc.queryForObject("select count(*) from "+name,Long.class));
            } catch (DataAccessException ex) {
                out.put(name,"ERROR: "+safeSqlError(ex));
                LOG.error("Knowledge catalog diagnostics table failed: {}",name,ex);
            }
        }
        try {
            out.put("rulesJoinSample",jdbc.queryForObject("""
                select count(*) from (select r.id from knowledge_rule r
                join knowledge_source_node sn on sn.id=r.source_node_id
                join knowledge_source_artifact a on a.id=sn.artifact_id
                join knowledge_source_document d on d.id=a.document_id limit 1) z
                """,Long.class));
        } catch (DataAccessException ex) {
            out.put("rulesJoinSample","ERROR: "+safeSqlError(ex));
            LOG.error("Knowledge catalog rule joins failed",ex);
        }
        return out;
    }

    private static String safeSqlError(DataAccessException ex) {
        String s=Objects.toString(ex.getMostSpecificCause()==null?ex.getMessage():ex.getMostSpecificCause().getMessage(),"SQL error");
        return s.length()>500?s.substring(0,500):s;
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
        boolean ready=classificationReady();
        String name=originalNameExpression();
        String join="""
            from knowledge_source_document d
            left join knowledge_intake_inventory i on i.id = (
                select ii.id from knowledge_intake_inventory ii
                where ii.document_id=d.id order by ii.updated_at desc,ii.id desc limit 1)
            """+classificationJoin(ready);
        List<Object> args=new ArrayList<>();
        StringBuilder filter=new StringBuilder(" where 1=1 ");
        if (!pattern(search).isEmpty()) {
            filter.append(" and (lower(coalesce(").append(name)
                  .append(",'')) like lower(?) or lower(coalesce(i.relative_path,'')) like lower(?)) ");
            args.add(like(search));args.add(like(search));
        }
        if (!"ALL".equals(category)) {
            filter.append(" and coalesce(c.category,'UNCLASSIFIED')=? ");args.add(category);
        }
        String from=join+filter;
        try {
            long total=jdbc.queryForObject("select count(*) "+from,Long.class,args.toArray());
            List<Object> pageArgs=new ArrayList<>(args);pageArgs.add(n);pageArgs.add((p-1)*n);
            List<Map<String,Object>> items=jdbc.queryForList("select d.id::text as id,"+name+" as original_name,"
                +"d.sha256,i.relative_path,i.detected_title,i.detected_category as folder_category,"
                +"i.page_count,i.intake_status,i.execution_id::text as execution_id,"
                +"coalesce(c.category,'UNCLASSIFIED') as category,c.notes,c.reviewed_by,c.reviewed_at,"
                +"(select count(*) from knowledge_rule r join knowledge_source_node sn on sn.id=r.source_node_id "
                +"join knowledge_source_artifact a on a.id=sn.artifact_id where a.document_id=d.id) as extracted_rule_count "
                +from+" order by "+name+",d.id limit ? offset ?",pageArgs.toArray());
            return result(items,total,p,n,ready);
        } catch (DataAccessException ex) {
            LOG.error("Catalog documents query failed: page={} size={} category={}",p,n,category,ex);
            return fallbackDocuments(p,n,search,category,ready,name,ex);
        }
    }

    private Map<String,Object> fallbackDocuments(int p,int n,String search,String category,
                                                   boolean ready,String name,DataAccessException cause) {
        if (!"ALL".equals(category) && !"UNCLASSIFIED".equals(category))
            throw cause; // Do not invent categories on the degraded read path.
        String predicate=pattern(search).isBlank()?"":" where lower(coalesce("+name+",'')) like lower(?) ";
        List<Object> q=new ArrayList<>();if(!pattern(search).isBlank())q.add(like(search));
        long total=jdbc.queryForObject("select count(*) from knowledge_source_document d"+predicate,
                                       Long.class,q.toArray());
        q.add(n);q.add((p-1)*n);
        List<Map<String,Object>> rows=jdbc.queryForList("select d.id::text id,"+name+
                " original_name,d.sha256 from knowledge_source_document d"+predicate+
                " order by "+name+",d.id limit ? offset ?",q.toArray());
        for(Map<String,Object> row:rows){
            row.put("category","UNCLASSIFIED");row.put("relative_path",null);
            row.put("extracted_rule_count",0L);
            row.put("diagnostic","Degraded read: investigate Backend SQL logs");
        }
        return result(rows,total,p,n,ready);
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
        boolean ready=classificationReady();
        String name=originalNameExpression();
        String join="""
            from knowledge_rule r
            join knowledge_source_node sn on sn.id=r.source_node_id
            join knowledge_source_artifact a on a.id=sn.artifact_id
            join knowledge_source_document d on d.id=a.document_id
            left join knowledge_rule_review v on v.rule_id=r.id
            """+classificationJoin(ready);
        List<Object> args=new ArrayList<>();
        StringBuilder filter=new StringBuilder(" where 1=1 ");
        if(!pattern(search).isEmpty()){
            filter.append(" and (lower(coalesce(r.stable_key,'')) like lower(?) "
                         +"or lower(coalesce(r.text_value,'')) like lower(?) "
                         +"or lower(coalesce(").append(name).append(",'')) like lower(?)) ");
            args.add(like(search));args.add(like(search));args.add(like(search));
        }
        if(!"ALL".equals(category)){
            filter.append(" and coalesce(c.category,'UNCLASSIFIED')=? ");args.add(category);
        }
        if(!"ALL".equals(status)){
            filter.append(" and r.status=? ");args.add(status);
        }
        if(executionId!=null){
            filter.append(" and a.execution_id=? ");args.add(executionId);
        }
        String from=join+filter;
        try {
            long total=jdbc.queryForObject("select count(*) "+from,Long.class,args.toArray());
            List<Object> pageArgs=new ArrayList<>(args);pageArgs.add(n);pageArgs.add((p-1)*n);
            List<Map<String,Object>> items=jdbc.queryForList("""
                select r.id::text as id,r.stable_key,r.operator,r.numeric_value,r.text_value,
                       r.confidence,r.status,r.jurisdiction_code,r.valid_from,r.valid_to,
                       sn.page_from,
                """+name+" as original_name,d.id::text as document_id,d.sha256,"
                +"a.execution_id::text as execution_id,coalesce(c.category,'UNCLASSIFIED') as category,"
                +"v.decision,v.reviewer,v.reviewed_at,v.subject_code,v.unit_code,"
                +"v.scope_city,v.source_authority "
                +from+" order by "+name+",sn.page_from,r.stable_key,r.id limit ? offset ?",
                pageArgs.toArray());
            return result(items,total,p,n,ready);
        } catch(DataAccessException ex) {
            LOG.error("Catalog rules query failed: page={} category={} status={} execution={}",p,category,status,executionId,ex);
            return fallbackRules(p,n,search,category,status,executionId,ready,name,ex);
        }
    }

    private Map<String,Object> fallbackRules(int p,int n,String search,String category,String status,
            UUID executionId,boolean ready,String name,DataAccessException cause) {
        if(!"ALL".equals(category) && !"UNCLASSIFIED".equals(category))throw cause;
        String join="""
            from knowledge_rule r
            join knowledge_source_node sn on sn.id=r.source_node_id
            join knowledge_source_artifact a on a.id=sn.artifact_id
            join knowledge_source_document d on d.id=a.document_id
            """;
        StringBuilder filter=new StringBuilder(" where 1=1 ");List<Object> args=new ArrayList<>();
        if(!pattern(search).isEmpty()){
            filter.append(" and lower(coalesce(").append(name).append(",'')) like lower(?) ");
            args.add(like(search));
        }
        if(!"ALL".equals(status)){filter.append(" and r.status=? ");args.add(status);}
        if(executionId!=null){filter.append(" and a.execution_id=? ");args.add(executionId);}
        String from=join+filter;
        long total=jdbc.queryForObject("select count(*) "+from,Long.class,args.toArray());
        args.add(n);args.add((p-1)*n);
        List<Map<String,Object>> rows=jdbc.queryForList("select r.id::text as id,r.stable_key,"
            +"r.operator,r.numeric_value,r.text_value,r.confidence,r.status,sn.page_from,"
            +name+" as original_name,d.id::text document_id,d.sha256 "
            +from+" order by sn.page_from,r.id limit ? offset ?",args.toArray());
        for(Map<String,Object> row:rows){row.put("category","UNCLASSIFIED");
            row.put("diagnostic","Degraded read: investigate Backend SQL logs");}
        return result(rows,total,p,n,ready);
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
