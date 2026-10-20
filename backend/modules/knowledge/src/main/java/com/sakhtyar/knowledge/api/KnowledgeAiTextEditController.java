package com.sakhtyar.knowledge.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Human edit of AI intake text. Source evidence is preserved, never silently rewritten. */
@RestController
@RequestMapping("/api/v1/knowledge/admin/ai-workspace")
public class KnowledgeAiTextEditController {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;

    public KnowledgeAiTextEditController(JdbcTemplate db, TransactionTemplate tx) {
        this.db = db;
        this.tx = tx;
    }

    private static String reviewer(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getAuthorities().stream()
            .noneMatch(a -> "USER_MANAGE".equals(a.getAuthority()))) {
            throw new AccessDeniedException("Knowledge management permission required");
        }
        return auth.getName();
    }

    private static String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 is not available", error);
        }
    }

    public record TextEdit(String fullText, String expectedHash) {}

    @PostMapping("/items/{id}/edit-text")
    public Map<String, Object> edit(@PathVariable UUID id, @RequestBody TextEdit request, Authentication auth) {
        String actor = reviewer(auth);
        if (request == null || request.fullText() == null || request.expectedHash() == null)
            throw new IllegalArgumentException("Text and current SHA-256 are required");
        String edited = request.fullText().strip();
        if (edited.length() < 12 || edited.length() > 20000)
            throw new IllegalArgumentException("Edited text length must be 12 to 20000 characters");
        if (edited.indexOf((char)0) >= 0)
            throw new IllegalArgumentException("Text contains a forbidden NUL (U+0000) character");
        final String newHash = digest(edited);
        return tx.execute(status -> {
            List<Map<String,Object>> matches = db.queryForList("""
                select i.id::text as id, i.batch_id, i.full_text, i.content_sha256,
                       i.review_status, i.evidence_quote, d.title as document_title
                  from knowledge_ai_import_item i
                  join knowledge_ai_import_document d on d.id=i.document_id
                  join knowledge_ai_import_batch b on b.id=i.batch_id
                 where i.id=? and i.archived_at is null and d.archived_at is null
                       and b.archived_at is null for update of i
                """, id);
            if (matches.size() != 1) throw new IllegalArgumentException("Active review record not found");
            Map<String,Object> old = matches.get(0);
            String previousHash = String.valueOf(old.get("content_sha256"));
            if (!previousHash.equalsIgnoreCase(request.expectedHash()))
                throw new IllegalArgumentException("Record changed since opening. Reload before editing");
            Long published = db.queryForObject("""
                select count(*) from knowledge_ai_publication
                 where item_id=? and revoked_at is null
                """, Long.class, id);
            if (published != null && published > 0)
                throw new IllegalArgumentException("Revoke catalogue publication before editing this record");
            String original = String.valueOf(old.get("full_text"));
            if (original.equals(edited))
                return Map.of("updated", false, "fullText", original, "contentSha256", previousHash,
                    "reviewStatus", String.valueOf(old.get("review_status")));
            db.update("""
                insert into knowledge_ai_text_revision
                (id,item_id,document_title,old_full_text,new_full_text,old_sha256,new_sha256,editor)
                values(?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), id, old.get("document_title"), original, edited,
                previousHash, newHash, actor);
            db.update("""
                update knowledge_ai_import_item set full_text=?, content_sha256=?,
                    review_status='PENDING_REVIEW',review_notes=null,reviewed_by=null,reviewed_at=null
                where id=?
                """, edited, newHash, id);
            db.update("update knowledge_ai_import_batch set status='AWAITING_REVIEW' where id=?",
                old.get("batch_id"));
            db.update("""
                insert into knowledge_ai_lifecycle_event
                (actor,event_type,scope_type,scope_id,affected_count,note)
                values(?,'EDIT_TEXT','ITEM',?,1,?)
                """, actor, id.toString(), "Human correction to staged extraction; review reset; original evidence retained");
            return Map.of("updated", true, "fullText", edited, "contentSha256", newHash,
                "reviewStatus", "PENDING_REVIEW", "evidenceMatches", edited.contains(String.valueOf(old.get("evidence_quote"))));
        });
    }

    @GetMapping("/items/{id}/text-history")
    public List<Map<String,Object>> history(@PathVariable UUID id, Authentication auth) {
        reviewer(auth);
        return db.queryForList("""
            select id::text, editor, edited_at, old_full_text, new_full_text
            from knowledge_ai_text_revision where item_id=? order by edited_at desc limit 20
            """, id);
    }
}
