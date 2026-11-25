package com.sakhtyar.knowledge.governance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only projection of legally UNVERIFIED AI staging records. No DML and no
 * automated promotion to knowledge_rule, knowledge_release or urban_rule.
 */
@Service
public class RegulationCandidateBridgeService {
    private final JdbcTemplate jdbc;
    public RegulationCandidateBridgeService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static void requireManager() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getAuthorities() == null ||
            auth.getAuthorities().stream().noneMatch(a -> "USER_MANAGE".equals(a.getAuthority())))
            throw new AccessDeniedException("USER_MANAGE required");
    }

    @Transactional(readOnly = true)
    public Map<String,Object> candidates(int requestedPage, int requestedSize, String requestedType) {
        requireManager();
        int page = Math.max(1, Math.min(requestedPage, 100000));
        int size = Math.max(1, Math.min(requestedSize, 100));
        String type = requestedType == null ? "ALL" : requestedType.strip().toUpperCase(Locale.ROOT);
        if (!List.of("ALL", "RULE", "SEGMENT").contains(type))
            throw new IllegalArgumentException("recordType must be RULE, SEGMENT or ALL");
        String filter = """
            from knowledge_ai_import_item i
            join knowledge_ai_import_document d on d.id = i.document_id
            join knowledge_ai_import_batch b on b.id = i.batch_id
            join knowledge_ai_publication pub on pub.item_id = i.id
            where i.review_status = 'APPROVED' and i.archived_at is null
              and d.archived_at is null and b.archived_at is null
              and d.category = 'LAW' and pub.publication_kind = 'LAW'
              and pub.revoked_at is null and pub.source_sha256 = i.content_sha256
              and (? = 'ALL' or i.record_type = ?)
            """;
        Long total = jdbc.queryForObject("select count(*) " + filter, Long.class, type, type);
        // These are proposals only. Exact PDF hash and page may suggest a source-node
        // link, but cannot establish legal authenticity or human verification.
        List<Map<String,Object>> rows = jdbc.queryForList("""
            select i.id as item_id,i.batch_id,i.document_id as ai_document_id,
                   i.record_type,i.segment_type,i.article_label,i.full_text,i.evidence_quote,
                   i.page_from,i.page_to,i.content_sha256,i.review_status,
                   d.source_sha256,d.authority,d.source_reference,d.territorial_scope,
                   d.effective_from,d.effective_to,
                   coalesce(link.matches,0) as source_matches,
                   link.source_document_id,link.source_node_id,link.original_source_sha256,
                   (select count(*) from knowledge_ai_import_item i2
                     where i2.document_id=i.document_id and i2.content_sha256=i.content_sha256)
                     as same_document_hash_count
            """ + """
            from knowledge_ai_import_item i
            join knowledge_ai_import_document d on d.id = i.document_id
            join knowledge_ai_import_batch b on b.id = i.batch_id
            join knowledge_ai_publication pub on pub.item_id = i.id
            left join lateral (
                select count(*) as matches,
                       min(sd.id::text) as source_document_id,
                       min(n.id::text) as source_node_id,
                       min(sd.sha256) as original_source_sha256
                from knowledge_source_document sd
                join knowledge_source_artifact a on a.document_id=sd.id
                join knowledge_source_node n on n.artifact_id=a.id
                where d.source_sha256 is not null and sd.sha256=d.source_sha256
                  and n.page_from <= i.page_from and n.page_to >= i.page_to
            ) link on true
            where i.review_status = 'APPROVED' and i.archived_at is null
              and d.archived_at is null and b.archived_at is null
              and d.category = 'LAW' and pub.publication_kind = 'LAW'
              and pub.revoked_at is null and pub.source_sha256 = i.content_sha256
              and (? = 'ALL' or i.record_type = ?)
            order by i.created_at,i.id limit ? offset ?
            """, type, type, size, (page - 1) * size);
        List<Map<String,Object>> out = new ArrayList<>(rows.size());
        for (Map<String,Object> row : rows) {
            long matches = ((Number)row.get("source_matches")).longValue();
            String state = matches == 0 ? "BLOCKED_MISSING_SOURCE" :
                    matches == 1 ? "SOURCE_LINK_PROPOSED" : "BLOCKED_AMBIGUOUS_SOURCE";
            String id = Objects.toString(row.get("item_id"));
            String hash = Objects.toString(row.get("content_sha256"), "");
            var suggestion = RegulationCandidatePolicy.propose(Objects.toString(row.get("full_text"), ""));
            var result = new LinkedHashMap<String,Object>(row);
            result.put("sourceKind", "AI_IMPORT_STAGING");
            result.put("provenanceState", state);
            result.put("reviewState", "HUMAN_REVIEW_REQUIRED");
            result.put("legalStatus", "NOT_VERIFIED_NOT_PUBLISHED");
            result.put("fingerprint", RegulationCandidatePolicy.fingerprint(id, hash,
                    Objects.toString(row.get("article_label"), "")));
            result.put("suggestion", suggestion);
            result.put("relationshipProposal", "NOTE".equals(row.get("segment_type")) ||
                    "CLAUSE".equals(row.get("segment_type")) ? "REVIEW_ARTICLE_DEPENDENCY" : "NONE");
            out.add(result);
        }
        return Map.of("page", page, "pageSize", size, "total", total == null ? 0 : total,
            "items", out, "publication", "NONE", "note", "AI catalogue approval is NOT legal verification");
    }
}
