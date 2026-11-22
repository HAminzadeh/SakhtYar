package com.sakhtyar.knowledge.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/knowledge/admin/legal")
public class KnowledgeLegalSegmentController {
    private final JdbcTemplate jdbc;
    public KnowledgeLegalSegmentController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private void requireAdmin(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() ||
            auth.getAuthorities().stream().noneMatch(a -> "USER_MANAGE".equals(a.getAuthority())))
            throw new AccessDeniedException("USER_MANAGE required");
    }

    @GetMapping("/segments")
    public Map<String,Object> segments(@RequestParam UUID executionId,
                                         @RequestParam(defaultValue="1") int page,
                                         @RequestParam(defaultValue="20") int pageSize,
                                         Authentication auth) {
        requireAdmin(auth);
        int size = Math.max(1, Math.min(50, pageSize));
        int current = Math.max(1, Math.min(100000, page));
        int offset = (current - 1) * size;
        Long total = jdbc.queryForObject(
            "select count(*) from knowledge_legal_segment where execution_id=?", Long.class, executionId);
        List<Map<String,Object>> rows = jdbc.queryForList("""
            select s.id::text as id,s.execution_id::text as execution_id,
                   s.document_id::text as document_id, s.rule_id::text as rule_id,
                   coalesce(nullif(d.filename,''), d.canonical_path) as document_name,
                   s.ordinal,s.segment_type,s.article_label,s.full_text,
                   s.page_from,s.page_to,s.text_sha256,s.review_status,
                   s.model_metadata->'qwen'->>'status' as qwen_status,
                   s.model_metadata->'qwen'->>'topic' as qwen_topic,
                   s.model_metadata->'qwen'->>'evidence_quote' as qwen_evidence,
                   s.model_metadata->>'sentence_count' as sentence_count
              from knowledge_legal_segment s
              join knowledge_source_document d on d.id=s.document_id
             where s.execution_id=?
             order by d.filename,s.page_from,s.ordinal
             limit ? offset ?
            """, executionId, size, offset);
        return Map.of("items", rows, "total", total == null ? 0L : total,
                      "page", current, "pageSize", size);
    }
}
