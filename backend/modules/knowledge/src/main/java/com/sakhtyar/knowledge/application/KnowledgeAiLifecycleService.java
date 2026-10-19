package com.sakhtyar.knowledge.application;

import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.regex.*;
import java.time.LocalDate;

/** Lifecycle actions affect only AI-import STAGING; never canonical rules, releases, or source PDFs. */
@Service
public class KnowledgeAiLifecycleService {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    public KnowledgeAiLifecycleService(JdbcTemplate db, TransactionTemplate tx, ObjectMapper json) {
        this.db=db;this.tx=tx;this.json=json;
    }
    private String encode(Object value) {
        return json.writeValueAsString(value);
    }
    private void audit(String actor,String event,String kind,String id,int count,String note,Object details){
        db.update("insert into knowledge_ai_lifecycle_event(actor,event_type,scope_type,scope_id,affected_count,note,details) values(?,?,?,?,?,?,?::jsonb)",
            actor,event,kind,id,count,note,encode(details));
    }
    private static int bounded(Integer x,int fallback,int max){return x==null?fallback:Math.max(1,Math.min(max,x));}
    public Map<String,Object> overview(){
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("batches",db.queryForObject("select count(*) from knowledge_ai_import_batch where archived_at is null",Long.class));
        result.put("documents",db.queryForObject("select count(*) from knowledge_ai_import_document d join knowledge_ai_import_batch b on b.id=d.batch_id where d.archived_at is null and b.archived_at is null",Long.class));
        result.put("rules",db.queryForObject("select count(*) from knowledge_ai_import_item i join knowledge_ai_import_document d on d.id=i.document_id join knowledge_ai_import_batch b on b.id=i.batch_id where i.archived_at is null and d.archived_at is null and b.archived_at is null and i.record_type='RULE'",Long.class));
        result.put("segments",db.queryForObject("select count(*) from knowledge_ai_import_item i join knowledge_ai_import_document d on d.id=i.document_id join knowledge_ai_import_batch b on b.id=i.batch_id where i.archived_at is null and d.archived_at is null and b.archived_at is null and i.record_type='SEGMENT'",Long.class));
        result.put("education",db.queryForObject("select count(*) from knowledge_ai_import_item i join knowledge_ai_import_document d on d.id=i.document_id join knowledge_ai_import_batch b on b.id=i.batch_id where i.archived_at is null and d.archived_at is null and b.archived_at is null and i.record_type='SEGMENT' and d.category in ('EDUCATION','TECHNICAL','REFERENCE','OTHER')",Long.class));
        result.put("pending",db.queryForObject("select count(*) from knowledge_ai_import_item i join knowledge_ai_import_document d on d.id=i.document_id join knowledge_ai_import_batch b on b.id=i.batch_id where i.archived_at is null and d.archived_at is null and b.archived_at is null and i.review_status='PENDING_REVIEW'",Long.class));
        result.put("issues",db.queryForObject("select count(*) from knowledge_ai_import_issue where state='OPEN'",Long.class));
        result.put("archived",db.queryForObject("select count(*) from knowledge_ai_import_item where archived_at is not null",Long.class));
        return result;
    }
    public Map<String,Object> packages(Integer page,Integer size,Boolean includingArchived,String search){
        int take=bounded(size,25,100), offset=(bounded(page,1,100000)-1)*take;
        boolean incl=Boolean.TRUE.equals(includingArchived);
        String filter=" where 1=1"+(incl?"":" and b.archived_at is null");
        List<Object> args=new ArrayList<>();
        if(search!=null&&!search.isBlank()){
            filter+=" and b.archive_name ilike ?";args.add("%"+search.strip()+"%");
        }
        long total=db.queryForObject("select count(*) from knowledge_ai_import_batch b"+filter,Long.class,args.toArray());
        List<Object> pg=new ArrayList<>(args);pg.add(take);pg.add(offset);
        List<Map<String,Object>> rows=db.queryForList("""
           select b.id::text,b.archive_name,b.source_ai,b.source_mode,b.created_at,b.status,b.archived_at,
            (select count(*) from knowledge_ai_import_document d where d.batch_id=b.id and d.archived_at is null) documents,
            (select count(*) from knowledge_ai_import_item i where i.batch_id=b.id and i.record_type='RULE' and i.archived_at is null) rules,
            (select count(*) from knowledge_ai_import_item i where i.batch_id=b.id and i.record_type='SEGMENT' and i.archived_at is null) segments,
            (select count(*) from knowledge_ai_import_issue x where x.batch_id=b.id and x.state='OPEN') issues
           from knowledge_ai_import_batch b
           """+filter+" order by b.created_at desc,b.id limit ? offset ?",pg.toArray());
        return Map.of("items",rows,"total",total,"page",offset/take+1,"size",take);
    }
    public Map<String,Object> documents(Integer page,Integer size,UUID batch,String kind,String query,Boolean includingArchived){
        int take=bounded(size,25,100), offset=(bounded(page,1,100000)-1)*take;
        String type=kind==null?"ALL":kind.toUpperCase(Locale.ROOT);
        if(!Set.of("ALL","LAW","EDUCATION","TECHNICAL","REFERENCE","OTHER","UNCLASSIFIED").contains(type))throw new IllegalArgumentException("Invalid category");
        StringBuilder where=new StringBuilder(" where 1=1");List<Object> args=new ArrayList<>();
        if(!Boolean.TRUE.equals(includingArchived))where.append(" and d.archived_at is null and b.archived_at is null");
        if(batch!=null){where.append(" and d.batch_id=?");args.add(batch);}
        if(!"ALL".equals(type)){where.append(" and d.category=?");args.add(type);}
        if(query!=null&&!query.isBlank()){where.append(" and (d.title ilike ? or d.source_reference ilike ? or d.regulation_family ilike ?)");String like="%"+query.strip().replace("%","\\%").replace("_","\\_")+"%";args.add(like);args.add(like);args.add(like);}
        String from=" from knowledge_ai_import_document d join knowledge_ai_import_batch b on b.id=d.batch_id ";
        long total=db.queryForObject("select count(*)"+from+where,Long.class,args.toArray());
        List<Object> pg=new ArrayList<>(args);pg.add(take);pg.add(offset);
        List<Map<String,Object>> items=db.queryForList("""
            select d.id::text,d.doc_key,d.title,d.category,d.suggested_category,d.classification_reason,
             d.source_reference,d.authority,d.source_sha256,d.batch_id::text,b.archive_name,d.archived_at,
             d.regulation_family,d.edition_label,d.calendar_type,d.resolution_date,d.publication_date,
             d.effective_from,d.effective_to,d.territorial_scope,d.relation_to_previous,d.previous_edition_id::text,
             (select count(*) from knowledge_ai_import_item i where i.document_id=d.id and i.archived_at is null) item_count
            """+from+where+" order by b.created_at desc,d.title,d.id limit ? offset ?",pg.toArray());
        return Map.of("items",items,"total",total,"page",offset/take+1,"size",take);
    }
    public Map<String,Object> items(Integer page,Integer size,UUID batch,UUID document,String category,String search,Boolean includingArchived){
        int take=bounded(size,25,100), offset=(bounded(page,1,100000)-1)*take;
        String cat=category==null?"ALL":category.toUpperCase(Locale.ROOT);
        if(!Set.of("ALL","LAW","EDUCATION","TECHNICAL","REFERENCE","OTHER","UNCLASSIFIED","NONLAW").contains(cat))throw new IllegalArgumentException("Invalid category");
        StringBuilder where=new StringBuilder(" where 1=1");List<Object> args=new ArrayList<>();
        if(!Boolean.TRUE.equals(includingArchived))where.append(" and i.archived_at is null and d.archived_at is null and b.archived_at is null");
        if(batch!=null){where.append(" and i.batch_id=?");args.add(batch);}
        if(document!=null){where.append(" and i.document_id=?");args.add(document);}
        if("NONLAW".equals(cat))where.append(" and d.category <> 'LAW'");
        else if(!"ALL".equals(cat)){where.append(" and d.category=?");args.add(cat);}
        if(search!=null&&!search.isBlank()){where.append(" and (i.full_text ilike ? or i.article_label ilike ? or d.title ilike ?)");String like="%"+search.strip()+"%";args.add(like);args.add(like);args.add(like);}
        String from=" from knowledge_ai_import_item i join knowledge_ai_import_document d on d.id=i.document_id join knowledge_ai_import_batch b on b.id=i.batch_id ";
        long total=db.queryForObject("select count(*)"+from+where,Long.class,args.toArray());
        List<Object> pg=new ArrayList<>(args);pg.add(take);pg.add(offset);
        List<Map<String,Object>> rows=db.queryForList("""
            select i.id::text,i.batch_id::text,i.document_id::text,i.item_key,i.record_type,i.segment_type,
             i.article_label,i.full_text,i.evidence_quote,i.page_from,i.page_to,i.content_sha256,
             i.review_status,i.archived_at,d.title as document_title,d.doc_key,d.category,d.source_reference,b.archive_name
            """+from+where+" order by b.created_at desc,d.title,i.page_from,i.item_key limit ? offset ?",pg.toArray());
        return Map.of("items",rows,"total",total,"page",offset/take+1,"size",take);
    }
    public record Scope(String type,List<UUID> ids,String category) {
        public Scope(String type,List<UUID> ids){this(type,ids,null);}
    }
    private Scope check(Scope selection){
        if(selection==null||!Set.of("ALL","BATCH","DOCUMENT","ITEM","CATEGORY").contains(selection.type())) throw new IllegalArgumentException("Invalid delete scope");
        if("ALL".equals(selection.type()))return new Scope("ALL",List.of());
        if("CATEGORY".equals(selection.type())){
            if(selection.category()==null||!Set.of("LAW","EDUCATION","TECHNICAL","REFERENCE","OTHER","UNCLASSIFIED").contains(selection.category()))
                throw new IllegalArgumentException("Select an exact document category for category cleanup");
            return new Scope("CATEGORY",List.of(),selection.category());
        }
        if(selection.ids()==null||selection.ids().isEmpty()||selection.ids().size()>2000||selection.ids().contains(null)) throw new IllegalArgumentException("Select 1-2000 IDs");
        return new Scope(selection.type(),List.copyOf(new LinkedHashSet<>(selection.ids())));
    }
    private String placeholder(int n){return String.join(",",Collections.nCopies(n,"?"));}
    private List<Map<String,Object>> selectedItems(Scope raw, boolean onlyVisible){
        Scope s=check(raw);String extra=onlyVisible?" and i.archived_at is null and d.archived_at is null and b.archived_at is null":"";
        String filter;
        List<Object> params=new ArrayList<>();
        if("ALL".equals(s.type())){filter="1=1";}
        else if("CATEGORY".equals(s.type())){filter="d.category=?";params.add(s.category());}
        else {String column=switch(s.type()) {case "BATCH"->"i.batch_id";case "DOCUMENT"->"i.document_id";case "ITEM"->"i.id";default->throw new IllegalArgumentException("Scope");};
            filter=column+" in ("+placeholder(s.ids().size())+")";params.addAll(s.ids());}
        return db.queryForList("""
          select i.id::text,i.batch_id::text,i.document_id::text,i.item_key,i.record_type,i.segment_type,
           i.article_label,i.full_text,i.evidence_quote,i.page_from,i.page_to,i.content_sha256,
           i.review_status,i.review_notes,i.reviewed_by,i.reviewed_at,i.archived_at,
           d.title as document_title,d.category,d.source_reference
          from knowledge_ai_import_item i join knowledge_ai_import_document d on d.id=i.document_id
          join knowledge_ai_import_batch b on b.id=i.batch_id where
          """+filter+extra+" order by i.id",params.toArray());
    }
    public Map<String,Object> preview(Scope selection){
        List<Map<String,Object>> rows=selectedItems(selection,true);
        long legal=rows.stream().filter(r->"LAW".equals(r.get("category"))).count();
        long approved=rows.stream().filter(r->"APPROVED".equals(r.get("review_status"))).count();
        return Map.of("items",rows.size(),"laws",legal,"approved",approved,"scope",selection.type(),
          "notice","Only AI-import staging is affected. Canonical regulations, published releases and source PDFs are NEVER deleted.");
    }
    public Map<String,Object> archive(Scope selection,String reason,String actor){
        if(reason==null||reason.strip().length()<8)throw new IllegalArgumentException("A reason of at least 8 characters is required");
        return tx.execute(t->{
            List<Map<String,Object>> rows=selectedItems(selection,true);
            for(Map<String,Object> row:rows)db.update("update knowledge_ai_import_item set archived_at=now(),archived_by=?,archive_reason=? where id=?::uuid and archived_at is null",actor,reason,row.get("id"));
            Scope s=check(selection);
            if("BATCH".equals(s.type()))for(UUID id:s.ids())db.update("update knowledge_ai_import_batch set archived_at=now(),archived_by=?,archive_reason=? where id=? and archived_at is null",actor,reason,id);
            if("DOCUMENT".equals(s.type()))for(UUID id:s.ids())db.update("update knowledge_ai_import_document set archived_at=now(),archived_by=?,archive_reason=? where id=? and archived_at is null",actor,reason,id);
            if("ALL".equals(s.type())){
                db.update("update knowledge_ai_import_batch set archived_at=now(),archived_by=?,archive_reason=? where archived_at is null",actor,reason);
                db.update("update knowledge_ai_import_document set archived_at=now(),archived_by=?,archive_reason=? where archived_at is null",actor,reason);
            }
            audit(actor,"ARCHIVE",s.type(),s.ids().toString(),rows.size(),reason,Map.of("itemIds",rows.stream().map(r->r.get("id")).toList()));
            return Map.of("archived",rows.size());
        });
    }
    public Map<String,Object> restore(Scope selection,String actor){
        return tx.execute(t->{
            List<Map<String,Object>> rows=selectedItems(selection,false);
            int changed=0;
            for(Map<String,Object> r:rows)changed+=db.update("update knowledge_ai_import_item set archived_at=null,archived_by=null,archive_reason=null where id=?::uuid and archived_at is not null",r.get("id"));
            // Restore parent visibility for any selected item, even when the parent was archived earlier.
            for(Map<String,Object> r:rows){
                db.update("update knowledge_ai_import_document set archived_at=null,archived_by=null,archive_reason=null where id=?::uuid",r.get("document_id"));
                db.update("update knowledge_ai_import_batch set archived_at=null,archived_by=null,archive_reason=null where id=?::uuid",r.get("batch_id"));
            }
            Scope ss=check(selection);
            if("BATCH".equals(ss.type()))for(UUID id:ss.ids())db.update("update knowledge_ai_import_batch set archived_at=null,archived_by=null,archive_reason=null where id=?",id);
            if("DOCUMENT".equals(ss.type()))for(UUID id:ss.ids())db.update("update knowledge_ai_import_document set archived_at=null,archived_by=null,archive_reason=null where id=?",id);
            if("ALL".equals(ss.type())){
                db.update("update knowledge_ai_import_batch set archived_at=null,archived_by=null,archive_reason=null where archived_at is not null");
                db.update("update knowledge_ai_import_document set archived_at=null,archived_by=null,archive_reason=null where archived_at is not null");
            }
            audit(actor,"RESTORE",selection.type(),String.valueOf(selection.ids()),changed,"Restored from archive",Map.of());
            return Map.of("restored",changed);
        });
    }
    public Map<String,Object> prepareBackup(Scope selection,String actor){
        return tx.execute(t->{
            List<Map<String,Object>> items=selectedItems(selection,false);
            if(items.size()>15000)throw new IllegalArgumentException("Export in selections of at most 15000 items");
            List<String> itemIds=items.stream().map(r->String.valueOf(r.get("id"))).toList();
            List<Map<String,Object>> fullItems=new ArrayList<>();
            List<Map<String,Object>> events=new ArrayList<>();
            if(!itemIds.isEmpty()) {
                for(int k=0;k<itemIds.size();k+=500){List<String> part=itemIds.subList(k,Math.min(itemIds.size(),k+500));
                    events.addAll(db.queryForList("select * from knowledge_ai_import_review_event where item_id::text in ("+placeholder(part.size())+")",part.toArray()));
                    fullItems.addAll(db.queryForList("select * from knowledge_ai_import_item where id::text in ("+placeholder(part.size())+")",part.toArray()));}
            }
            List<Map<String,Object>> parentDocuments=new ArrayList<>(),parentBatches=new ArrayList<>(),issueRows=new ArrayList<>();
            Set<String> documentIds=new LinkedHashSet<>(),batchIds=new LinkedHashSet<>();
            for(var r:items){documentIds.add(String.valueOf(r.get("document_id")));batchIds.add(String.valueOf(r.get("batch_id")));}
            for(String id:documentIds)parentDocuments.addAll(db.queryForList("select * from knowledge_ai_import_document where id=?::uuid",id));
            for(String id:batchIds){parentBatches.addAll(db.queryForList("select * from knowledge_ai_import_batch where id=?::uuid",id));
                issueRows.addAll(db.queryForList("select * from knowledge_ai_import_issue where batch_id=?::uuid",id));}
            Map<String,Object> snapshot=Map.of("items",fullItems,"documents",parentDocuments,"batches",parentBatches,
                "issues",issueRows,"reviewEvents",events,"selectionType",selection.type());
            UUID token=UUID.randomUUID();
            db.update("insert into knowledge_ai_delete_backup(id,actor,selection,snapshot,record_count) values(?,?,?::jsonb,?::jsonb,?)",
                token,actor,encode(selection),encode(snapshot),items.size());
            audit(actor,"BACKUP",selection.type(),token.toString(),items.size(),"Backup created",Map.of());
            return Map.of("backupId",token.toString(),"items",items.size(),"snapshot",snapshot);
        });
    }
    public Map<String,Object> permanentlyDelete(UUID backupId,String confirmation,String actor){
        if(!"DELETE STAGING PERMANENTLY".equals(confirmation))throw new IllegalArgumentException("Exact confirmation phrase required");
        return tx.execute(t->{
            List<Map<String,Object>> backups=db.queryForList("select actor,selection::text,snapshot::text,record_count,consumed_at from knowledge_ai_delete_backup where id=? for update",backupId);
            if(backups.size()!=1||backups.get(0).get("consumed_at")!=null||!actor.equals(backups.get(0).get("actor")))throw new IllegalArgumentException("Backup token invalid/consumed or actor changed");
            Map<String,Object> backup=backups.get(0);
            try {
                tools.jackson.databind.JsonNode sel=json.readTree(String.valueOf(backup.get("selection")));
                String type=sel.path("type").asText();List<UUID> ids=new ArrayList<>();for(var node:sel.path("ids"))ids.add(UUID.fromString(node.asText()));
                List<Map<String,Object>> present=selectedItems(new Scope(type,ids,sel.path("category").isNull()?null:sel.path("category").asText(null)),false);
                if(present.size()!=((Number)backup.get("record_count")).intValue())throw new IllegalArgumentException("Selection changed since backup; create a fresh backup");
                tools.jackson.databind.JsonNode saved=json.readTree(String.valueOf(backup.get("snapshot")));
                Set<String> beforeIds=new HashSet<>();for(var item:saved.path("items"))beforeIds.add(item.path("id").asText());
                Set<String> currentIds=new HashSet<>();for(var item:present)currentIds.add(String.valueOf(item.get("id")));
                if(!beforeIds.equals(currentIds))throw new IllegalArgumentException("Selection IDs changed since backup; create a fresh backup");
                // Immutable baseline and approved historical edition protect against destructive deletion.
                for(Map<String,Object> row:present){
                    Long versions=db.queryForObject("select count(*) from knowledge_ai_import_document where id=?::uuid and edition_reviewed_at is not null",Long.class,row.get("document_id"));
                    if(versions!=null&&versions>0)throw new IllegalArgumentException("Reviewed historical editions may be archived, but cannot be purged");
                }
                Set<String> frozenIds=new HashSet<>();
                for(Map<String,Object> x:db.queryForList("select snapshot::text as data from knowledge_ai_baseline")){
                    var frozen=json.readTree(String.valueOf(x.get("data")));
                    for(var entry:frozen.path("itemHashes"))frozenIds.add(entry.path("id").asText());
                }
                for(String itemId:currentIds)if(frozenIds.contains(itemId))
                    throw new IllegalArgumentException("This record belongs to a frozen baseline; archive it instead of purging it");
                // Purge only entities present in the preflight snapshot, never unrelated graph or canonical knowledge.
                for(Map<String,Object> row:present){String item=String.valueOf(row.get("id"));
                    db.update("delete from knowledge_ai_import_review_event where item_id=?::uuid",item);
                    db.update("delete from knowledge_ai_import_item where id=?::uuid",item);
                }
                // Delete a parent only if all its dependent records are gone.
                for(Map<String,Object> row:present){String doc=String.valueOf(row.get("document_id"));
                    db.update("delete from knowledge_ai_import_document where id=?::uuid and not exists (select 1 from knowledge_ai_import_item where document_id=?::uuid)",doc,doc);
                }
                for(Map<String,Object> row:present){String batch=String.valueOf(row.get("batch_id"));
                    db.update("delete from knowledge_ai_import_issue where batch_id=?::uuid and not exists (select 1 from knowledge_ai_import_document where batch_id=?::uuid)",batch,batch);
                    db.update("delete from knowledge_ai_import_batch where id=?::uuid and not exists (select 1 from knowledge_ai_import_document where batch_id=?::uuid)",batch,batch);
                }
                db.update("update knowledge_ai_delete_backup set consumed_at=now() where id=?",backupId);
                audit(actor,"PURGE",type,backupId.toString(),present.size(),"Hard delete with server-side backup",Map.of());
                return Map.of("deleted",present.size(),"backupId",backupId.toString());
            }catch(tools.jackson.core.JacksonException ex){throw new IllegalArgumentException("Invalid stored backup",ex);}
        });
    }
    public List<Map<String,Object>> issues(UUID batch,String state,Integer limit){
        String condition=batch==null?"":" and batch_id=?";
        if(state!=null&&!Set.of("OPEN","RESOLVED","DISMISSED","ALL").contains(state))throw new IllegalArgumentException("Bad issue filter");
        String st=state==null||"ALL".equals(state)?"":" and state='"+state+"'";
        if(batch==null)return db.queryForList("select id::text,batch_id::text,origin_file,line_number,doc_key,item_key,issue_code,explanation,suggestion,state,row_payload,created_at from knowledge_ai_import_issue where 1=1"+st+" order by created_at desc limit ?",bounded(limit,100,1000));
        return db.queryForList("select id::text,batch_id::text,origin_file,line_number,doc_key,item_key,issue_code,explanation,suggestion,state,row_payload,created_at from knowledge_ai_import_issue where 1=1"+condition+st+" order by created_at desc limit ?",batch,bounded(limit,100,1000));
    }
    public Map<String,Object> decideIssue(UUID id,String decision,String note,String actor){
        if(!Set.of("RESOLVED","DISMISSED").contains(decision)||note==null||note.strip().length()<8)throw new IllegalArgumentException("Decision and explanatory note required");
        int n=db.update("update knowledge_ai_import_issue set state=?,decision_by=?,decision_note=?,resolved_at=now() where id=? and state='OPEN'",decision,actor,note,id);
        if(n!=1)throw new IllegalArgumentException("Issue not found/open");
        audit(actor,"ISSUE_"+decision,"ISSUE",id.toString(),1,note,Map.of());
        return Map.of("updated",1);
    }
    public Map<String,Object> updateEdition(UUID id, Map<String,Object> values,String actor){
        List<Map<String,Object>> exists=db.queryForList("select category from knowledge_ai_import_document where id=?",id);
        if(exists.isEmpty())throw new IllegalArgumentException("Document not found");
        // No date is ever inferred from a Persian year label; store exact Gregorian ISO dates only when verified.
        String family=str(values.get("regulationFamily"),220),edition=str(values.get("editionLabel"),100);
        String calendar=str(values.get("calendarType"),20),scope=str(values.get("territorialScope"),2000);
        String relation=str(values.get("relationToPrevious"),40);
        if(calendar!=null&&!Set.of("JALALI","GREGORIAN","UNKNOWN").contains(calendar))throw new IllegalArgumentException("Invalid calendar");
        if(relation!=null&&!Set.of("NEW","AMENDMENT","SUPERSEDES","PARTIAL_REPEAL","REFERENCE").contains(relation))throw new IllegalArgumentException("Invalid relation");
        LocalDate resolution=date(values.get("resolutionDate")),publication=date(values.get("publicationDate")),from=date(values.get("effectiveFrom")),to=date(values.get("effectiveTo"));
        if(from!=null&&to!=null&&to.isBefore(from))throw new IllegalArgumentException("Effective-to precedes effective-from");
        db.update("""
            update knowledge_ai_import_document set regulation_family=?,edition_label=?,calendar_type=?,territorial_scope=?,
            relation_to_previous=?,resolution_date=?,publication_date=?,effective_from=?,effective_to=?,
            edition_reviewed_by=?,edition_reviewed_at=now() where id=?
            """,family,edition,calendar,scope,relation,resolution,publication,from,to,actor,id);
        audit(actor,"EDITION_METADATA","DOCUMENT",id.toString(),1,"Edition metadata reviewed",values);
        return Map.of("updated",1);
    }
    public Map<String,Object> classify(UUID id,String category,String reason,String actor){
        if(!Set.of("LAW","EDUCATION","TECHNICAL","REFERENCE","OTHER","UNCLASSIFIED").contains(category))throw new IllegalArgumentException("Category invalid");
        if(reason==null||reason.strip().length()<12)throw new IllegalArgumentException("Document classification requires a rationale");
        if("LAW".equals(category))throw new IllegalArgumentException("LAW classification needs authoritative source review in the dedicated governance workflow");
        int n=db.update("update knowledge_ai_import_document set category=?,classification_confirmed_by=? where id=?",category,actor,id);
        audit(actor,"CLASSIFICATION","DOCUMENT",id.toString(),n,reason,Map.of("category",category));return Map.of("updated",n);
    }
    private static String str(Object v,int max){if(v==null)return null;String s=String.valueOf(v).trim();if(s.length()>max)throw new IllegalArgumentException("Value too long");return s.isEmpty()?null:s;}
    private static LocalDate date(Object val){String s=str(val,20);return s==null?null:LocalDate.parse(s);}
    public Map<String,Object> editions(String family){
        if(family==null||family.isBlank())return Map.of("items",List.of());
        return Map.of("items",db.queryForList("""
          select id::text,title,category,source_reference,regulation_family,edition_label,calendar_type,
           publication_date,effective_from,effective_to,territorial_scope,relation_to_previous,edition_reviewed_at
          from knowledge_ai_import_document where regulation_family=? and archived_at is null
          order by effective_from nulls last,publication_date nulls last,edition_label,id
          """,family));
    }
    public Map<String,Object> compare(UUID left,UUID right){
        if(left.equals(right))throw new IllegalArgumentException("Choose two distinct editions");
        List<Map<String,Object>> docs=db.queryForList("select id::text,title,regulation_family,edition_label,category,source_reference from knowledge_ai_import_document where id in (?,?)",left,right);
        if(docs.size()!=2)throw new IllegalArgumentException("Both documents must exist");
        String f1=String.valueOf(docs.get(0).get("regulation_family")),f2=String.valueOf(docs.get(1).get("regulation_family"));
        if("null".equals(f1)||!f1.equals(f2))throw new IllegalArgumentException("Edition comparison requires the same reviewed regulation family");
        List<Map<String,Object>> a=editionRows(left),b=editionRows(right);
        Map<String,Map<String,Object>> aa=new LinkedHashMap<>(),bb=new LinkedHashMap<>();
        for(var v:a)aa.putIfAbsent(key(v),v);for(var v:b)bb.putIfAbsent(key(v),v);
        List<Map<String,Object>> diff=new ArrayList<>();Set<String> keys=new LinkedHashSet<>(aa.keySet());keys.addAll(bb.keySet());
        for(String k:keys){Map<String,Object> x=aa.get(k),y=bb.get(k);
            String status=x==null?"ADDED":y==null?"REMOVED":Objects.equals(x.get("content_sha256"),y.get("content_sha256"))?"UNCHANGED":"MODIFIED";
            if(!"UNCHANGED".equals(status)){
                Map<String,Object> row=new LinkedHashMap<>();row.put("key",k);row.put("status",status);row.put("before",x);row.put("after",y);diff.add(row);
            }
        }
        return Map.of("before",left.toString(),"after",right.toString(),"changes",diff,
            "warning","Text alignment is a proposal; expert must verify renumbered, amended and repealed provisions.");
    }
    private List<Map<String,Object>> editionRows(UUID id){return db.queryForList("select item_key,segment_type,article_label,full_text,content_sha256,page_from from knowledge_ai_import_item where document_id=? and archived_at is null order by page_from,item_key",id);}
    private String key(Map<String,Object> m){Object label=m.get("article_label");return String.valueOf(m.get("segment_type"))+":"+(label==null||String.valueOf(label).isBlank()?m.get("item_key"):label);}
    /** Historical lookup returns candidates, never a final legal opinion. */
    public Map<String,Object> asOf(LocalDate target,String family){
        if(target==null)throw new IllegalArgumentException("Query date required (ISO Gregorian)");
        String f=family==null||family.isBlank()?"":" and d.regulation_family=?";
        List<Map<String,Object>> rows=family==null||family.isBlank()?db.queryForList("""
          select d.id::text,d.title,d.regulation_family,d.edition_label,d.source_reference,d.territorial_scope,
                 d.publication_date,d.effective_from,d.effective_to
          from knowledge_ai_import_document d join knowledge_ai_import_batch b on b.id=d.batch_id
          where d.category='LAW' and d.edition_reviewed_at is not null and d.archived_at is null and b.archived_at is null
          and d.effective_from<=? and (d.effective_to is null or d.effective_to>=?) order by d.regulation_family,d.effective_from desc
          """,target,target) : db.queryForList("""
          select d.id::text,d.title,d.regulation_family,d.edition_label,d.source_reference,d.territorial_scope,
                 d.publication_date,d.effective_from,d.effective_to
          from knowledge_ai_import_document d join knowledge_ai_import_batch b on b.id=d.batch_id
          where d.category='LAW' and d.edition_reviewed_at is not null and d.archived_at is null and b.archived_at is null
          and d.effective_from<=? and (d.effective_to is null or d.effective_to>=?) and d.regulation_family=?
          order by d.effective_from desc
          """,target,target,family);
        return Map.of("items",rows,"date",target.toString(),"disclaimer",
           "Candidate editions based only on reviewed dates; applicable territory, amendments, repeal, and authoritative legal status need professional validation.");
    }
    public List<Map<String,Object>> duplicates(Integer limit){
        return db.queryForList("""
          select i.content_sha256,count(*) as occurrences,count(distinct i.batch_id) as batches,
                 min(d.title) as example_title
          from knowledge_ai_import_item i join knowledge_ai_import_document d on d.id=i.document_id
          join knowledge_ai_import_batch b on b.id=i.batch_id
          where i.archived_at is null and d.archived_at is null and b.archived_at is null
          group by i.content_sha256 having count(*)>1 order by occurrences desc limit ?
          """,bounded(limit,100,1000));
    }
    public Map<String,Object> createBaseline(String label,String note,String actor){
        if(label==null||label.strip().length()<4||label.length()>220)throw new IllegalArgumentException("Baseline title 4..220 required");
        List<Map<String,Object>> itemHashes=db.queryForList("""
            select i.id::text,i.content_sha256,i.review_status,d.id::text as document_id
            from knowledge_ai_import_item i join knowledge_ai_import_document d on d.id=i.document_id
            join knowledge_ai_import_batch b on b.id=i.batch_id
            where i.archived_at is null and d.archived_at is null and b.archived_at is null order by i.id
            """);
        if(itemHashes.size()>150000)throw new IllegalArgumentException("Too many rows to freeze in one baseline");
        Map<String,Object> snap=Map.of("overview",overview(),"itemHashes",itemHashes);UUID id=UUID.randomUUID();
        db.update("insert into knowledge_ai_baseline(id,label,note,snapshot,created_by) values(?,?,?,?::jsonb,?)",id,label.strip(),note,encode(snap),actor);
        audit(actor,"BASELINE_CREATE","BASELINE",id.toString(),itemHashes.size(),note,Map.of("label",label));
        return Map.of("id",id.toString(),"count",itemHashes.size(),"snapshot",snap);
    }
    public List<Map<String,Object>> baselines(){return db.queryForList("select id::text,label,note,created_by,created_at,snapshot from knowledge_ai_baseline order by created_at desc limit 100");}
    public List<Map<String,Object>> events(Integer limit){return db.queryForList("select id::text,actor,event_type,scope_type,scope_id,affected_count,note,created_at from knowledge_ai_lifecycle_event order by created_at desc limit ?",bounded(limit,100,500));}
}
