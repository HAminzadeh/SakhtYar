package com.sakhtyar.knowledge.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Read-only expert-curated AI knowledge; NOT the authoritative legal rule engine. */
@RestController
@RequestMapping("/api/v1/knowledge/ai-catalogue")
public class KnowledgeAiCatalogueController {
    private final JdbcTemplate db;
    public KnowledgeAiCatalogueController(JdbcTemplate db){this.db=db;}

    @GetMapping("/search")
    public Map<String,Object> search(@RequestParam(defaultValue="ALL") String category,
        @RequestParam(required=false) String q,@RequestParam(required=false) Integer page,
        @RequestParam(required=false) Integer size){
        String cat=category==null?"ALL":category.toUpperCase(Locale.ROOT);
        if(!Set.of("LAW","NONLAW","ALL").contains(cat))throw new IllegalArgumentException("Invalid category");
        if(q!=null&&q.length()>160)throw new IllegalArgumentException("Search is too long");
        int n=size==null?20:Math.max(1,Math.min(size,100)),p=page==null?1:Math.max(1,Math.min(page,100000));
        String from=" from knowledge_ai_publication pub join knowledge_ai_import_item i on i.id=pub.item_id " +
            "join knowledge_ai_import_document d on d.id=i.document_id join knowledge_ai_import_batch b on b.id=i.batch_id";
        String where=" where pub.revoked_at is null and pub.source_sha256=i.content_sha256 " +
            "and i.review_status='APPROVED' and i.archived_at is null and d.archived_at is null and b.archived_at is null";
        if("LAW".equals(cat))where+=" and d.category='LAW'";
        else if("NONLAW".equals(cat))where+=" and d.category<>'LAW'";
        List<Object> args=new ArrayList<>();
        if(q!=null&&!q.isBlank()){
            where+=" and (d.title ilike ? or i.full_text ilike ? or i.article_label ilike ?)";
            for(int k=0;k<3;k++)args.add("%"+q.strip()+"%");
        }
        Long total=db.queryForObject("select count(*)"+from+where,Long.class,args.toArray());
        List<Object> paging=new ArrayList<>(args);paging.add(n);paging.add((p-1)*n);
        List<Map<String,Object>> items=db.queryForList("""
            select i.id::text as id,d.id::text as document_id,d.title as document_title,d.category,d.source_reference,d.authority,
             d.edition_label,d.effective_from,d.effective_to,i.article_label,i.record_type,i.segment_type,
             i.full_text,i.evidence_quote,i.page_from,i.page_to,pub.published_at,pub.published_by,
             pub.review_note,'EXPERT_REVIEWED_NOT_OFFICIAL' as legal_status
            """+from+where+" order by pub.published_at desc,i.id limit ? offset ?",paging.toArray());
        return Map.of("items",items,"total",total==null?0L:total,"page",p,"size",n,
            "notice","Expert-curated candidate catalogue, not an authoritative legal rule/release.");
    }
}