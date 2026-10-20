package com.sakhtyar.knowledge.api;

import com.sakhtyar.knowledge.application.KnowledgeAiLifecycleService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import java.nio.file.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Curation of AI staging records: expert-reviewed publication is NOT legal enactment. */
@RestController
@RequestMapping("/api/v1/knowledge/admin/ai-curation")
public class KnowledgeAiCurationController {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final KnowledgeAiLifecycleService lifecycle;

    public KnowledgeAiCurationController(JdbcTemplate db, TransactionTemplate tx, KnowledgeAiLifecycleService lifecycle) {
        this.db = db; this.tx = tx; this.lifecycle = lifecycle;
    }

    private String manager(Authentication a) {
        if (a == null || !a.isAuthenticated() || a.getAuthorities().stream().noneMatch(v -> "USER_MANAGE".equals(v.getAuthority())))
            throw new AccessDeniedException("USER_MANAGE required");
        return a.getName();
    }
    private String admin(Authentication a) {
        String who = manager(a);
        if (a.getAuthorities().stream().noneMatch(v -> "ROLE_ADMIN".equals(v.getAuthority())))
            throw new AccessDeniedException("ADMIN required");
        return who;
    }
    private static String allowed(String value, Set<String> choices, String fallback) {
        String v = value == null || value.isBlank() ? fallback : value.toUpperCase(Locale.ROOT);
        if (!choices.contains(v)) throw new IllegalArgumentException("Invalid filter or scope: " + v);
        return v;
    }
    private static int bounded(Integer input, int fallback, int max) {
        return input == null ? fallback : Math.max(1, Math.min(input, max));
    }
    private static String placeholders(int n) {return String.join(",", Collections.nCopies(n,"?"));}
    private void audit(String actor, String operation, String scope, int affected, String note) {
        db.update("insert into knowledge_ai_lifecycle_event(actor,event_type,scope_type,affected_count,note) values(?,?,?,?,?)",
            actor, operation, scope, affected, note);
    }

    public record Filter(String category, String recordType, String search, List<UUID> batchIds) {}
    public record Bulk(Filter filter, String scope, List<UUID> ids, String decision, Integer expectedCount, boolean confirmed) {}
    private record SqlFilter(String fromWhere, List<Object> args) {}

    private SqlFilter pendingFilter(Filter f) {
        Filter v = f == null ? new Filter("ALL","ALL",null,null) : f;
        String category = allowed(v.category(), Set.of("LAW","NONLAW","ALL"), "ALL");
        String recordType = allowed(v.recordType(), Set.of("RULE","SEGMENT","ALL"), "ALL");
        StringBuilder sql = new StringBuilder(" from knowledge_ai_import_item i join knowledge_ai_import_document d on d.id=i.document_id " +
            "join knowledge_ai_import_batch b on b.id=i.batch_id where i.review_status='PENDING_REVIEW' " +
            "and i.archived_at is null and d.archived_at is null and b.archived_at is null");
        List<Object> args = new ArrayList<>();
        if ("NONLAW".equals(category)) sql.append(" and d.category<>'LAW'");
        else if ("LAW".equals(category)) sql.append(" and d.category='LAW'");
        if (!"ALL".equals(recordType)) { sql.append(" and i.record_type=?"); args.add(recordType); }
        if (v.batchIds() != null) {
            LinkedHashSet<UUID> unique = new LinkedHashSet<>(v.batchIds());
            if (unique.contains(null) || unique.size() > 20000) throw new IllegalArgumentException("Invalid package selection");
            if (unique.isEmpty()) sql.append(" and 1=0");
            else {sql.append(" and i.batch_id in (").append(placeholders(unique.size())).append(")");args.addAll(unique);}
        }
        if (v.search() != null && !v.search().isBlank()) {
            if (v.search().length() > 160) throw new IllegalArgumentException("Search text too long");
            String like="%" + v.search().strip() + "%";
            sql.append(" and (i.full_text ilike ? or i.article_label ilike ? or d.title ilike ? or d.source_reference ilike ?)");
            for(int k=0;k<4;k++) args.add(like);
        }
        return new SqlFilter(sql.toString(),args);
    }

    private List<Map<String,Object>> targetPending(Bulk b, boolean lock) {
        if (b==null || !b.confirmed()) throw new IllegalArgumentException("Explicit confirmation required");
        String scope=allowed(b.scope(),Set.of("SELECTED","PAGE","ALL"),"SELECTED");
        SqlFilter f=pendingFilter(b.filter());
        List<Object> args=new ArrayList<>(f.args());
        String sql="select i.id::text as id,i.batch_id::text as batch_id" + f.fromWhere();
        if (!"ALL".equals(scope)) {
            LinkedHashSet<UUID> ids = new LinkedHashSet<>(b.ids()==null?List.of():b.ids());
            if (ids.isEmpty() || ids.size()>1000 || ids.contains(null) || ids.size()!=b.ids().size())
                throw new IllegalArgumentException("Select 1-1000 unique visible row IDs");
            sql += " and i.id in ("+placeholders(ids.size())+")";
            args.addAll(ids);
        }
        sql+=" order by i.id";
        if(lock)sql+=" for update of i";
        List<Map<String,Object>> selected = db.queryForList(sql,args.toArray());
        if(selected.isEmpty() || selected.size()>15000 || b.expectedCount()==null || b.expectedCount()!=selected.size())
            throw new IllegalArgumentException("Count has changed or selection is empty / exceeds 15000. Refresh preview.");
        return selected;
    }

    @PostMapping("/review/preview")
    public Map<String,Object> reviewPreview(@RequestBody Bulk request,Authentication auth) {
        manager(auth);
        // For preview the caller does not yet know the count; calculate independently.
        String scope=allowed(request.scope(),Set.of("SELECTED","PAGE","ALL"),"SELECTED");
        SqlFilter f=pendingFilter(request.filter());
        List<Object> args=new ArrayList<>(f.args());
        String sql="select count(*)"+f.fromWhere();
        if (!"ALL".equals(scope)) {
            LinkedHashSet<UUID> ids=new LinkedHashSet<>(request.ids()==null?List.of():request.ids());
            if(ids.isEmpty()||ids.size()>1000||ids.contains(null))throw new IllegalArgumentException("Select rows first");
            sql+=" and i.id in ("+placeholders(ids.size())+")";args.addAll(ids);
        }
        Long count=db.queryForObject(sql,Long.class,args.toArray());
        return Map.of("count",count==null?0:count,"scope",scope,"publication",false);
    }

    @PostMapping("/review/apply")
    public Map<String,Object> bulkReview(@RequestBody Bulk request,Authentication auth) {
        String actor=manager(auth);
        String decision=allowed(request.decision(),Set.of("APPROVED","REJECTED"),"APPROVED");
        return tx.execute(t->{
            List<Map<String,Object>> rows=targetPending(request,true);
            List<String> ids=rows.stream().map(x->String.valueOf(x.get("id"))).toList();
            String note=decision.equals("APPROVED")
                ?"بازبینی گروهی با تأیید صریح کاربر؛ صرفاً پذیرش نامزد دانش برای مرحله انتشار و نه تأیید رسمیت منبع."
                :"بازبینی گروهی با تصمیم صریح کاربر؛ رد نامزد دانش به علت کیفیت یا اعتبار استناد.";
            for(int pos=0;pos<ids.size();pos+=250) {
                List<String> part=ids.subList(pos,Math.min(pos+250,ids.size()));
                String marks=placeholders(part.size());
                List<Object> upd=new ArrayList<>(List.of(decision,note,actor));upd.addAll(part);
                int changed=db.update("update knowledge_ai_import_item set review_status=?,review_notes=?,reviewed_by=?,reviewed_at=now() " +
                    "where review_status='PENDING_REVIEW' and id::text in ("+marks+")",upd.toArray());
                if(changed!=part.size())throw new IllegalStateException("Concurrent update; no decisions saved");
                List<Object> ins=new ArrayList<>(List.of(decision,note,actor));ins.addAll(part);
                db.update("insert into knowledge_ai_import_review_event(id,item_id,decision,notes,reviewer) " +
                    "select gen_random_uuid(),id,?,?,? from knowledge_ai_import_item where id::text in ("+marks+")",
                    ins.toArray());
            }
            LinkedHashSet<String> batches=new LinkedHashSet<>();
            for(var row:rows)batches.add(String.valueOf(row.get("batch_id")));
            for(String batch:batches)db.update("update knowledge_ai_import_batch b set status=case when exists " +
                "(select 1 from knowledge_ai_import_item i where i.batch_id=b.id and i.review_status='PENDING_REVIEW') " +
                "then 'AWAITING_REVIEW' else 'REVIEWED' end where b.id=?::uuid",batch);
            audit(actor,"REVIEW_BULK",request.scope(),rows.size(),note);
            return Map.<String,Object>of("updated",rows.size(),"publication","NOT_PUBLISHED");
        });
    }

    public record Deletion(KnowledgeAiLifecycleService.Scope selection,Integer expectedCount,String confirmation) {}
    @PostMapping("/delete/preview")
    public Map<String,Object> deletePreview(@RequestBody KnowledgeAiLifecycleService.Scope selection,Authentication auth) {
        admin(auth);
        if(selection==null)throw new IllegalArgumentException("Selection required");
        String type=allowed(selection.type(),Set.of("ITEM","ALL"),"ITEM");
        List<Object> args=new ArrayList<>();
        String where="";
        if(type.equals("ITEM")){
            List<UUID> ids=selection.ids();
            if(ids==null||ids.isEmpty()||ids.size()>2000||ids.contains(null))throw new IllegalArgumentException("Select 1-2000 rows");
            where=" where id in ("+placeholders(ids.size())+")";args.addAll(new LinkedHashSet<>(ids));
        }
        Long count=db.queryForObject("select count(*) from knowledge_ai_import_item"+where,Long.class,args.toArray());
        Long published=db.queryForObject("select count(*) from knowledge_ai_publication p join knowledge_ai_import_item i on i.id=p.item_id"+
            (where.isEmpty()?"":" where i.id in ("+placeholders(args.size())+")")+ (where.isEmpty()?" where":" and")+" p.revoked_at is null",
            Long.class,args.toArray());
        return Map.of("count",count==null?0L:count,"published",published==null?0L:published,
            "notice","Permanent deletion is limited to AI staging; published or historically protected records cannot be deleted.");
    }
    @PostMapping("/delete/apply")
    public Map<String,Object> directDelete(@RequestBody Deletion request,Authentication auth){
        String actor=admin(auth);
        if(request==null||!"DELETE STAGING PERMANENTLY".equals(request.confirmation()))
            throw new IllegalArgumentException("Exact confirmation phrase required");
        if(request.expectedCount()==null||request.expectedCount()<1||request.expectedCount()>15000)
            throw new IllegalArgumentException("Deletion requires a preview between 1 and 15000 records");
        return tx.execute(t->{
            Map<String,Object> preview=deletePreview(request.selection(),auth);
            if(((Number)preview.get("count")).intValue()!=request.expectedCount())
                throw new IllegalArgumentException("Selection changed. Preview again.");
            if(((Number)preview.get("published")).longValue()>0)
                throw new IllegalArgumentException("Some selected records are published. Revoke publication before deleting.");
            // Automatic audit backup: user is not required to manually create or download one.
            Map<String,Object> snapshot=lifecycle.prepareBackup(request.selection(),actor);
            if(((Number)snapshot.get("items")).intValue()!=request.expectedCount())
                throw new IllegalArgumentException("Backup selection changed. Nothing deleted.");
            // Revoked publication rows still hold ON DELETE RESTRICT FK references.
            // Remove *revoked metadata only* as part of this same transaction; audit remains.
            if("ALL".equals(request.selection().type()))
                db.update("delete from knowledge_ai_publication where revoked_at is not null");
            else {
                List<UUID> itemIds=request.selection().ids();
                db.update("delete from knowledge_ai_publication where revoked_at is not null and item_id in ("+
                    placeholders(itemIds.size())+")",itemIds.toArray());
            }
            UUID backupId=UUID.fromString((String)snapshot.get("backupId"));
            return lifecycle.permanentlyDelete(backupId,request.confirmation(),actor);
        });
    }

    public record DuplicateRequest(String scope,List<String> hashes,Integer expectedCount,boolean permanent,String confirmation) {}
    private record DedupPlan(List<UUID> victims,int preserved,int skipped,int groups) {}
    private DedupPlan duplicatePlan(DuplicateRequest req){
        String scope=allowed(req.scope(),Set.of("SELECTED","PAGE","ALL"),"SELECTED");
        List<String> hashes;
        if("ALL".equals(scope)) hashes=db.queryForList("""
           select content_sha256 from knowledge_ai_import_item where archived_at is null
           group by content_sha256 having count(*)>1 order by content_sha256 limit 20001
           """,String.class);
        else {
            hashes=List.copyOf(new LinkedHashSet<>(req.hashes()==null?List.of():req.hashes()));
            if(hashes.isEmpty()||hashes.size()>1000)throw new IllegalArgumentException("Choose duplicate groups first");
        }
        if(hashes.size()>20000)throw new IllegalArgumentException("Too many duplicate groups for one operation");
        List<UUID> victims=new ArrayList<>();int keep=0,skipped=0;
        for(String hash:hashes){
            if(hash==null||!hash.matches("[a-fA-F0-9]{64}"))throw new IllegalArgumentException("Invalid SHA-256");
            List<Map<String,Object>> items=db.queryForList("""
                select i.id::text as id,i.review_status,i.item_key,i.record_type,i.article_label,
                d.title,d.category,d.source_reference,coalesce(d.edition_label,'') as edition_label,
                case when p.item_id is not null and p.revoked_at is null then 1 else 0 end as published
                from knowledge_ai_import_item i join knowledge_ai_import_document d on d.id=i.document_id
                join knowledge_ai_import_batch b on b.id=i.batch_id
                left join knowledge_ai_publication p on p.item_id=i.id
                where i.content_sha256=? and i.archived_at is null and d.archived_at is null and b.archived_at is null
                order by case when p.item_id is not null and p.revoked_at is null then 0 else 1 end,
                         case when i.review_status='APPROVED' then 0 else 1 end,i.created_at,i.id
                """,hash);
            if(items.size()<2)continue;
            Set<String> contexts=new HashSet<>();
            for(var row:items)contexts.add(String.join("|",String.valueOf(row.get("title")),String.valueOf(row.get("category")),
                String.valueOf(row.get("source_reference")).split("\\|",2)[0].strip(),String.valueOf(row.get("edition_label")),
                String.valueOf(row.get("record_type")),String.valueOf(row.get("article_label"))));
            long publishedCount=items.stream().filter(r->((Number)r.get("published")).intValue()!=0).count();
            if(contexts.size()!=1 || publishedCount>1){skipped++;continue;}
            keep++;
            for(int index=1;index<items.size();index++)victims.add(UUID.fromString((String)items.get(index).get("id")));
        }
        if(victims.size()>15000)throw new IllegalArgumentException("Too many duplicate items, run in smaller groups");
        return new DedupPlan(victims,keep,skipped,hashes.size());
    }
    @GetMapping("/duplicate-groups")
    public Map<String,Object> duplicates(@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size,Authentication auth){
        manager(auth);
        int n=bounded(size,20,200),p=bounded(page,1,100000),offset=(p-1)*n;
        // Keep the exact hash groups, but disclose which ones CANNOT safely be cleaned.
        // The context expression mirrors duplicatePlan(): title/category/original filename/edition/type/article.
        String context="coalesce(d.title,'') || chr(30) || coalesce(d.category,'') || chr(30) || " +
            "btrim(split_part(coalesce(d.source_reference,''),'|',1)) || chr(30) || " +
            "coalesce(d.edition_label,'') || chr(30) || i.record_type || chr(30) || coalesce(i.article_label,'null')";
        String published="count(*) filter (where pub.item_id is not null and pub.revoked_at is null)";
        String from=" from knowledge_ai_import_item i join knowledge_ai_import_document d on d.id=i.document_id " +
            "join knowledge_ai_import_batch b on b.id=i.batch_id " +
            "left join knowledge_ai_publication pub on pub.item_id=i.id " +
            "where i.archived_at is null and d.archived_at is null and b.archived_at is null";
        String grouped="select i.content_sha256, count(*) as occurrences,count(distinct i.batch_id) as batches,"+
            "min(d.title) as example_title,"+
            "(count(distinct ("+context+"))=1 and "+published+"<=1) as cleanable,"+
            "case when count(distinct ("+context+"))>1 then 'DIFFERENT_SOURCE_EDITION_OR_ARTICLE' " +
            "when "+published+">1 then 'MULTIPLE_PUBLISHED' else 'SAFE_TO_CLEAN' end as protection_reason"+
            from+" group by i.content_sha256 having count(*)>1";
        Long total=db.queryForObject("select count(*) from ("+grouped+") grouped",Long.class);
        List<Map<String,Object>> items=db.queryForList("select * from ("+grouped+") grouped order by occurrences desc,content_sha256 limit ? offset ?",n,offset);
        return Map.of("items",items,"total",total==null?0:total,"page",p,"size",n);
    }
    @PostMapping("/duplicates/preview")
    public Map<String,Object> duplicatePreview(@RequestBody DuplicateRequest req,Authentication auth){
        manager(auth);DedupPlan p=duplicatePlan(req);
        return Map.of("remove",p.victims().size(),"preserved",p.preserved(),"skipped",p.skipped(),"groups",p.groups());
    }
    @PostMapping("/duplicates/apply")
    public Map<String,Object> deduplicate(@RequestBody DuplicateRequest req,Authentication auth){
        String actor=req.permanent()?admin(auth):manager(auth);
        if(!"DEDUPLICATE CONFIRMED".equals(req.confirmation()))throw new IllegalArgumentException("Exact deduplication confirmation required");
        return tx.execute(t->{
            DedupPlan plan=duplicatePlan(req);
            if(req.expectedCount()==null||req.expectedCount()!=plan.victims().size()||plan.victims().isEmpty())
                throw new IllegalArgumentException("Duplicate count changed. Preview again.");
            // Existing lifecycle scope is deliberately bounded to 2000 IDs. Chunk safely within
            // one outer transaction, so all chunks roll back if any fails or is protected.
            for(int offset=0;offset<plan.victims().size();offset+=1500){
                List<UUID> part=plan.victims().subList(offset,Math.min(offset+1500,plan.victims().size()));
                KnowledgeAiLifecycleService.Scope scope=new KnowledgeAiLifecycleService.Scope("ITEM",part);
                if(req.permanent()){
                    Long published=db.queryForObject("select count(*) from knowledge_ai_publication where item_id in ("+placeholders(part.size())+") and revoked_at is null",Long.class,part.toArray());
                    if(published!=null&&published>0)throw new IllegalArgumentException("Published duplicates cannot be removed");
                    Map<String,Object> backup=lifecycle.prepareBackup(scope,actor);
                    if(((Number)backup.get("items")).intValue()!=part.size())throw new IllegalStateException("Backup count mismatch");
                    db.update("delete from knowledge_ai_publication where revoked_at is not null and item_id in ("+
                        placeholders(part.size())+")",part.toArray());
                    lifecycle.permanentlyDelete(UUID.fromString((String)backup.get("backupId")),"DELETE STAGING PERMANENTLY",actor);
                }else lifecycle.archive(scope,"آرشیو تکراری‌های کاملاً هم‌منشأ با نگهداری یک نسخه قابل مشاهده",actor);
            }
            audit(actor,req.permanent()?"DEDUP_PURGE":"DEDUP_ARCHIVE",req.scope(),plan.victims().size(),"One representative retained per safe duplicate group");
            return Map.of("removed",plan.victims().size(),"preserved",plan.preserved(),"skipped",plan.skipped());
        });
    }

    public record Publication(String category,String scope,List<UUID> ids,Integer expectedCount,String note,boolean confirmed) {}
    private List<Map<String,Object>> publicationCandidates(Publication req){
        String category=allowed(req.category(),Set.of("LAW","NONLAW"),"LAW");
        String scope=allowed(req.scope(),Set.of("SELECTED","PAGE","ALL"),"SELECTED");
        String where=" where i.review_status='APPROVED' and i.archived_at is null and d.archived_at is null and b.archived_at is null"+
            ("LAW".equals(category)?" and d.category='LAW'":" and d.category<>'LAW'");
        List<Object> args=new ArrayList<>();
        if(!"ALL".equals(scope)){
            List<UUID> ids=req.ids()==null?List.of():req.ids();
            if(ids.isEmpty()||ids.size()>1000||ids.contains(null))throw new IllegalArgumentException("Select 1-1000 items");
            where+=" and i.id in ("+placeholders(ids.size())+")";args.addAll(ids);
        }
        return db.queryForList("""
          select i.id::text as id,i.content_sha256,d.category,d.title,d.source_reference
          from knowledge_ai_import_item i join knowledge_ai_import_document d on d.id=i.document_id
          join knowledge_ai_import_batch b on b.id=i.batch_id
          left join knowledge_ai_publication p on p.item_id=i.id
          """+where+" and (p.item_id is null or p.revoked_at is not null or p.source_sha256<>i.content_sha256)"+
          " order by i.id limit 15001",args.toArray());
    }
    @PostMapping("/publish/preview")
    public Map<String,Object> publishPreview(@RequestBody Publication req,Authentication auth){
        manager(auth);
        List<Map<String,Object>> rows=publicationCandidates(req);
        return Map.of("count",rows.size(),"category",allowed(req.category(),Set.of("LAW","NONLAW"),"LAW"));
    }
    @PostMapping("/publish/apply")
    public Map<String,Object> publish(@RequestBody Publication req,Authentication auth){
        String actor=admin(auth);
        if(!req.confirmed()||req.note()==null||req.note().strip().length()<12)
            throw new IllegalArgumentException("Explicit publication confirmation and a 12-character source verification note are required");
        return tx.execute(t->{
            List<Map<String,Object>> rows=publicationCandidates(req);
            if(rows.isEmpty()||rows.size()>15000||req.expectedCount()==null||rows.size()!=req.expectedCount())
                throw new IllegalArgumentException("Candidate count changed. Preview again.");
            String kind=allowed(req.category(),Set.of("LAW","NONLAW"),"LAW");
            for(var row:rows)db.update("""
              insert into knowledge_ai_publication(item_id,source_sha256,publication_kind,published_by,review_note,published_at,revoked_at)
              values (?::uuid,?,?,?,?,now(),null)
              on conflict(item_id) do update set source_sha256=excluded.source_sha256,publication_kind=excluded.publication_kind,
                published_by=excluded.published_by,review_note=excluded.review_note,published_at=now(),revoked_at=null
              """,row.get("id"),row.get("content_sha256"),kind,actor,req.note().strip());
            audit(actor,"PUBLISH_CATALOGUE",req.scope(),rows.size(),req.note().strip());
            return Map.<String,Object>of("published",rows.size(),"kind",kind,"notice","Expert-reviewed catalogue; not an authoritative legal publication");
        });
    }
    public record Revoke(List<UUID> ids,String confirmation) {}
    @PostMapping("/publish/revoke")
    public Map<String,Object> revoke(@RequestBody Revoke req,Authentication auth){
        String actor=admin(auth);
        if(req==null||!"REVOKE PUBLICATION".equals(req.confirmation())||req.ids()==null||req.ids().isEmpty()||req.ids().size()>1000)
            throw new IllegalArgumentException("Explicit revocation confirmation and 1-1000 IDs required");
        int changed=db.update("update knowledge_ai_publication set revoked_at=now() where item_id in ("+placeholders(req.ids().size())+") and revoked_at is null",req.ids().toArray());
        audit(actor,"REVOKE_CATALOGUE","ITEM",changed,"Publication revoked");
        return Map.of("revoked",changed);
    }
    @GetMapping("/publish/items")
    public Map<String,Object> publicationItems(@RequestParam(defaultValue="LAW") String category,@RequestParam(defaultValue="false") boolean published,
         @RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size,@RequestParam(required=false) String search,Authentication auth){
        manager(auth);
        String kind=allowed(category,Set.of("LAW","NONLAW"),"LAW");
        int n=bounded(size,20,1000),p=bounded(page,1,100000);
        String where=" where i.review_status='APPROVED' and i.archived_at is null and d.archived_at is null and b.archived_at is null"+
            ("LAW".equals(kind)?" and d.category='LAW'":" and d.category<>'LAW'");
        if(published)where+=" and p.revoked_at is null and p.item_id is not null and p.source_sha256=i.content_sha256";
        else where+=" and (p.item_id is null or p.revoked_at is not null or p.source_sha256<>i.content_sha256)";
        List<Object> args=new ArrayList<>();
        if(search!=null&&!search.isBlank()){
            if(search.length()>160)throw new IllegalArgumentException("Search too long");
            where+=" and (d.title ilike ? or i.full_text ilike ? or i.article_label ilike ?)";
            for(int k=0;k<3;k++)args.add("%"+search.strip()+"%");
        }
        String from=" from knowledge_ai_import_item i join knowledge_ai_import_document d on d.id=i.document_id " +
            "join knowledge_ai_import_batch b on b.id=i.batch_id left join knowledge_ai_publication p on p.item_id=i.id";
        Long total=db.queryForObject("select count(*)"+from+where,Long.class,args.toArray());
        List<Object> paging=new ArrayList<>(args);paging.add(n);paging.add((p-1)*n);
        List<Map<String,Object>> rows=db.queryForList("""
           select i.id::text as id,i.document_id::text as document_id,i.full_text,i.article_label,i.page_from,i.record_type,
                  d.title as document_title,d.category,d.source_reference,b.archive_name,i.reviewed_by,
                  p.published_at,p.published_by
           """+from+where+" order by i.reviewed_at desc nulls last,i.id limit ? offset ?",paging.toArray());
        return Map.of("items",rows,"total",total==null?0:total,"page",p,"size",n);
    }

    @GetMapping("/documents/{id}/source")
    public ResponseEntity<FileSystemResource> source(@PathVariable UUID id,Authentication auth) throws IOException {
        manager(auth);
        List<Map<String,Object>> found=db.queryForList("select source_reference from knowledge_ai_import_document where id=?",id);
        if(found.size()!=1)throw new IllegalArgumentException("Document not found");
        String ref=String.valueOf(found.get(0).get("source_reference"));
        String name="";
        for(String fragment:ref.split("\\|")){
            String candidate=fragment.trim().replace('\\','/');
            int end=candidate.toLowerCase(Locale.ROOT).indexOf(".pdf");
            if(end<0)continue;
            candidate=candidate.substring(0,end+4);
            candidate=candidate.substring(candidate.lastIndexOf('/')+1).trim();
            if(!candidate.isBlank()&&!candidate.contains("..")){name=candidate;break;}
        }
        if(name.isBlank()||!name.toLowerCase(Locale.ROOT).endsWith(".pdf")||name.contains(".."))
            throw new IllegalArgumentException("No verifiable PDF filename is recorded for this document");
        Path dir=Paths.get("").toAbsolutePath().normalize();
        Path docs=dir.resolve("DocumentationOfLawsAndRegulations");
        if(!Files.isDirectory(docs))docs=dir.resolve("..").resolve("DocumentationOfLawsAndRegulations").normalize();
        if(!Files.isDirectory(docs))docs=dir.resolve("../..").resolve("DocumentationOfLawsAndRegulations").normalize();
        if(!Files.isDirectory(docs))docs=dir.resolve("../../..").resolve("DocumentationOfLawsAndRegulations").normalize();
        if(!Files.isDirectory(docs))throw new IllegalArgumentException("PDF source folder not deployed on the server");
        Path selected=null;
        final String pdfName=name;
        try(var files=Files.walk(docs,7)){
            List<Path> matches=files.filter(Files::isRegularFile).filter(p->p.getFileName().toString().equalsIgnoreCase(pdfName)).limit(2).toList();
            if(matches.size()!=1)throw new IllegalArgumentException(matches.isEmpty()?"Source PDF not found":"Ambiguous source PDF filename");
            selected=matches.get(0).toRealPath();
        }
        if(!selected.startsWith(docs.toRealPath()))throw new AccessDeniedException("Invalid source path");
        ContentDisposition cd=ContentDisposition.attachment().filename(name,StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,cd.toString())
            .contentType(MediaType.APPLICATION_PDF).body(new FileSystemResource(selected));
    }
}
