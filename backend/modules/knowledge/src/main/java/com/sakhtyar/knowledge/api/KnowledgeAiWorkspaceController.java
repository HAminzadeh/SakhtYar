package com.sakhtyar.knowledge.api;

import com.sakhtyar.knowledge.application.KnowledgeAiImportService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Read-only cross-package workbench, except explicit reviewed item decisions. Never publishes rules. */
@RestController
@RequestMapping("/api/v1/knowledge/admin/ai-workspace")
public class KnowledgeAiWorkspaceController {
    private final JdbcTemplate db;
    private final KnowledgeAiImportService imported;
    public KnowledgeAiWorkspaceController(JdbcTemplate db,KnowledgeAiImportService imported) {
        this.db=db;this.imported=imported;
    }
    private String manager(Authentication auth) {
        if(auth==null || !auth.isAuthenticated() || auth.getAuthorities().stream().noneMatch(a->"USER_MANAGE".equals(a.getAuthority())))
            throw new AccessDeniedException("USER_MANAGE required");
        return auth.getName();
    }
    @GetMapping("/selector")
    public List<Map<String,Object>> selector(Authentication auth){
        manager(auth);
        return db.queryForList("""
           select b.id::text as id,b.archive_name,b.source_mode,b.created_at,
             (select count(*) from knowledge_ai_import_document d where d.batch_id=b.id and d.archived_at is null) as documents,
             (select count(*) from knowledge_ai_import_document d where d.batch_id=b.id and d.category='LAW' and d.archived_at is null) as law_documents,
             (select count(*) from knowledge_ai_import_document d where d.batch_id=b.id and d.category<>'LAW' and d.archived_at is null) as education_documents,
             (select count(*) from knowledge_ai_import_item i where i.batch_id=b.id and i.record_type='RULE' and i.archived_at is null) as rules,
             (select count(*) from knowledge_ai_import_item i join knowledge_ai_import_document d on d.id=i.document_id
                 where i.batch_id=b.id and d.category='LAW' and i.record_type='SEGMENT' and i.archived_at is null and d.archived_at is null) as legal_segments,
             (select count(*) from knowledge_ai_import_item i join knowledge_ai_import_document d on d.id=i.document_id
                 where i.batch_id=b.id and d.category<>'LAW' and i.record_type='SEGMENT' and i.archived_at is null and d.archived_at is null) as educational_segments
           from knowledge_ai_import_batch b where b.archived_at is null order by b.created_at desc,b.id limit 20000
           """);
    }
    private static int bounded(Integer input,int fallback,int maximum) {
        return input==null?fallback:Math.max(1,Math.min(maximum,input));
    }
    private static void batches(StringBuilder where,List<Object> args,List<UUID> ids) {
        if(ids==null)return;
        LinkedHashSet<UUID> safe=new LinkedHashSet<>(ids);
        if(safe.contains(null)||safe.size()>20000)throw new IllegalArgumentException("Invalid batch selection");
        if(safe.isEmpty()){where.append(" and 1=0");return;}
        where.append(" and i.batch_id in (").append(String.join(",",Collections.nCopies(safe.size(),"?"))).append(")");
        args.addAll(safe);
    }
    @GetMapping("/items")
    public Map<String,Object> items(@RequestParam(required=false) List<UUID> batchIds,
        @RequestParam(defaultValue="ALL") String category,@RequestParam(defaultValue="ALL") String recordType,
        @RequestParam(defaultValue="ALL") String reviewStatus,@RequestParam(required=false) String search,
        @RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size,Authentication auth){
        manager(auth);
        String cat=category.toUpperCase(Locale.ROOT),typ=recordType.toUpperCase(Locale.ROOT),status=reviewStatus.toUpperCase(Locale.ROOT);
        if(!Set.of("LAW","NONLAW","ALL","EDUCATION","TECHNICAL","REFERENCE","OTHER","UNCLASSIFIED").contains(cat))throw new IllegalArgumentException("Invalid category");
        if(!Set.of("RULE","SEGMENT","ALL").contains(typ))throw new IllegalArgumentException("Invalid record type");
        if(!Set.of("ALL","PENDING_REVIEW","APPROVED","REJECTED").contains(status))throw new IllegalArgumentException("Invalid review status");
        int take=bounded(size,20,1000),current=bounded(page,1,100000),offset=(current-1)*take;
        StringBuilder where=new StringBuilder(" where i.archived_at is null and d.archived_at is null and b.archived_at is null");
        List<Object> args=new ArrayList<>();batches(where,args,batchIds);
        if("NONLAW".equals(cat))where.append(" and d.category <> 'LAW'");
        else if(!"ALL".equals(cat)){where.append(" and d.category=?");args.add(cat);}
        if(!"ALL".equals(typ)){where.append(" and i.record_type=?");args.add(typ);}
        if(!"ALL".equals(status)){where.append(" and i.review_status=?");args.add(status);}
        if(search!=null&&!search.isBlank()){
            if(search.length()>160)throw new IllegalArgumentException("Search too long");
            String value="%"+search.strip()+"%";
            where.append(" and (i.full_text ilike ? or i.article_label ilike ? or d.title ilike ? or d.source_reference ilike ?)");
            for(int k=0;k<4;k++)args.add(value);
        }
        String from=" from knowledge_ai_import_item i join knowledge_ai_import_document d on d.id=i.document_id join knowledge_ai_import_batch b on b.id=i.batch_id";
        Long total=db.queryForObject("select count(*)"+from+where,Long.class,args.toArray());
        List<Object> paging=new ArrayList<>(args);paging.add(take);paging.add(offset);
        List<Map<String,Object>> rows=db.queryForList("""
            select i.id::text as id,i.batch_id::text as batch_id,i.document_id::text as document_id,
              i.item_key,i.record_type,i.segment_type,i.article_label,i.full_text,i.evidence_quote,
              i.page_from,i.page_to,i.review_status,i.content_sha256,d.title as document_title,
              d.category,d.source_reference,b.archive_name
            """+from+where+" order by b.created_at desc,d.title,i.page_from,i.item_key,i.id limit ? offset ?",paging.toArray());
        return Map.of("items",rows,"total",total==null?0L:total,"page",current,"size",take);
    }
    @GetMapping("/documents")
    public Map<String,Object> documents(@RequestParam(required=false) List<UUID> batchIds,
        @RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size,
        @RequestParam(defaultValue="ALL") String category,@RequestParam(required=false) String search,Authentication auth){
        manager(auth);
        String selectedCategory=category.toUpperCase(Locale.ROOT);
        if(!Set.of("ALL","LAW","NONLAW").contains(selectedCategory))throw new IllegalArgumentException("Invalid category");
        int take=bounded(size,20,1000),current=bounded(page,1,100000),offset=(current-1)*take;
        StringBuilder where=new StringBuilder(" where d.archived_at is null and b.archived_at is null");
        List<Object> args=new ArrayList<>();
        if(batchIds!=null){
            LinkedHashSet<UUID> safe=new LinkedHashSet<>(batchIds);
            if(safe.contains(null)||safe.size()>20000)throw new IllegalArgumentException("Invalid batch selection");
            if(safe.isEmpty())where.append(" and 1=0");
            else {where.append(" and d.batch_id in (").append(String.join(",",Collections.nCopies(safe.size(),"?"))).append(")");args.addAll(safe);}
        }
        if("LAW".equals(selectedCategory))where.append(" and d.category='LAW'");
        else if("NONLAW".equals(selectedCategory))where.append(" and d.category<>'LAW'");
        if(search!=null&&!search.isBlank()){
            if(search.length()>160)throw new IllegalArgumentException("Search too long");
            String value="%"+search.strip()+"%";where.append(" and (d.title ilike ? or d.source_reference ilike ?)");args.add(value);args.add(value);
        }
        String from=" from knowledge_ai_import_document d join knowledge_ai_import_batch b on b.id=d.batch_id";
        Long total=db.queryForObject("select count(*)"+from+where,Long.class,args.toArray());
        List<Object> paging=new ArrayList<>(args);paging.add(take);paging.add(offset);
        List<Map<String,Object>> rows=db.queryForList("""
           select d.id::text as id,d.title,d.category,d.doc_key,d.source_reference,d.authority,
             d.regulation_family,d.edition_label,d.batch_id::text as batch_id,b.archive_name,
             (select count(*) from knowledge_ai_import_item i where i.document_id=d.id and i.archived_at is null) as item_count
           """+from+where+" order by b.created_at desc,d.title,d.id limit ? offset ?",paging.toArray());
        return Map.of("items",rows,"total",total==null?0L:total,"page",current,"size",take);
    }
    public record Decision(UUID itemId,String decision) {}
    @PostMapping("/decision")
    public Map<String,Object> decision(@RequestBody Decision request,Authentication auth){
        String actor=manager(auth);
        if(request==null||request.itemId()==null||request.decision()==null)throw new IllegalArgumentException("Item and decision required");
        String next=request.decision().toUpperCase(Locale.ROOT);
        if(!Set.of("APPROVED","REJECTED","PENDING_REVIEW").contains(next))throw new IllegalArgumentException("Invalid review decision");
        List<Map<String,Object>> found=db.queryForList("""
            select i.batch_id::text as batch_id,i.review_status,d.category
            from knowledge_ai_import_item i join knowledge_ai_import_document d on d.id=i.document_id
             join knowledge_ai_import_batch b on b.id=i.batch_id
            where i.id=? and i.archived_at is null and d.archived_at is null and b.archived_at is null
            """,request.itemId());
        if(found.size()!=1)throw new IllegalArgumentException("Item not found in active staging");
        Map<String,Object> old=found.get(0);
        if(next.equals(old.get("review_status")))throw new IllegalArgumentException("This item already has the selected status. Refresh list.");
        String note=switch(next){
            case "APPROVED" -> "تصمیم صریح بازبین در گردش‌کار سریع: پذیرش نامزد دانش برای مرحله بعد؛ این اقدام انتشار یا احراز اعتبار قانونی نیست.";
            case "REJECTED" -> "تصمیم صریح بازبین در گردش‌کار سریع: رد به علت کیفیت، دقت استخراج یا استناد؛ نیازمند اصلاح و بازبینی.";
            default -> "تصمیم صریح بازبین در گردش‌کار سریع: بازگرداندن این رکورد به صف بازبینی و بررسی مجدد.";
        };
        UUID batch=UUID.fromString((String)old.get("batch_id"));
        return imported.review(batch,request.itemId(),next,note,actor);
    }
}
