package com.sakhtyar.knowledge.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class KnowledgeSelectedExecutionService {
    private final JdbcTemplate jdbc;

    public KnowledgeSelectedExecutionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Map<String,Object> createSelectionAndQueue(List<String> selectedIds, String user) {
        List<UUID> ids = new ArrayList<>();
        for (String s : selectedIds == null ? List.<String>of() : selectedIds) {
            try { ids.add(UUID.fromString(s)); } catch (Exception ignored) { }
        }
        if (ids.isEmpty()) throw new IllegalArgumentException("No valid documents selected");

        String selectionKey = "knowledge-intake-main";
        Map<String,Object> policy = new LinkedHashMap<>();
        policy.put("dedup", true);
        policy.put("nativeFirst", true);
        policy.put("ocr", "AUTO");
        policy.put("compute", "HYBRID");
        policy.put("processSelectedOnly", true);
        policy.put("versionedOutputs", true);

        String sourceHash = sha256(ids.toString());
        UUID oldId = jdbc.query("""
            select id from knowledge_intake_selection_version
            where selection_key=? and status='CURRENT'
            for update
            """, rs -> rs.next() ? (UUID) rs.getObject(1) : null, selectionKey);

        Integer oldVersion = oldId == null ? null : jdbc.query("""
            select version_no from knowledge_intake_selection_version where id=?
            """, rs -> rs.next() ? rs.getInt(1) : null, oldId);

        int version = oldVersion == null ? 1 : oldVersion + 1;
        if (oldId != null) {
            jdbc.update("update knowledge_intake_selection_version set status='SUPERSEDED' where id=?", oldId);
        }

        UUID selectionId = UUID.randomUUID();
        jdbc.update("""
            insert into knowledge_intake_selection_version
            (id,selection_key,version_no,status,selected_document_ids,processing_policy,
             source_report_hash,supersedes_id,selected_by)
            values(?,?,?,'CURRENT',cast(? as jsonb),cast(? as jsonb),?,?,?)
            """,
            selectionId, selectionKey, version, json(ids), json(policy),
            sourceHash, oldId, user == null ? "system" : user
        );

        UUID executionId = UUID.randomUUID();
        jdbc.update("""
            insert into knowledge_intake_execution
            (id,selection_version_id,status,total_documents,current_stage,requested_by)
            values(?,?,'QUEUED',?,'SELECTED_ONLY_INTAKE',?)
            """,
            executionId, selectionId, ids.size(), user == null ? "system" : user
        );

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("executionId", executionId);
        result.put("selectionVersionId", selectionId);
        result.put("selectionVersion", version);
        result.put("selected", ids.size());
        result.put("status", "QUEUED");
        result.put("policy", policy);
        result.put("createdAt", Instant.now().toString());
        return result;
    }

    public Map<String,Object> status(UUID id) {
        return jdbc.query("""
            select id,status,total_documents,completed_documents,failed_documents,current_stage,
                   statistics,error_message,created_at,started_at,finished_at
            from knowledge_intake_execution where id=?
            """, rs -> {
                if (!rs.next()) return Map.of("found", false);
                Map<String,Object> x = new LinkedHashMap<>();
                x.put("found", true);
                x.put("id", rs.getObject(1));
                x.put("status", rs.getString(2));
                x.put("totalDocuments", rs.getInt(3));
                x.put("completedDocuments", rs.getInt(4));
                x.put("failedDocuments", rs.getInt(5));
                x.put("currentStage", rs.getString(6));
                x.put("statistics", rs.getString(7));
                x.put("error", rs.getString(8));
                x.put("createdAt", rs.getObject(9));
                x.put("startedAt", rs.getObject(10));
                x.put("finishedAt", rs.getObject(11));
                return x;
            }, id);
    }

    private String json(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return quote(s);
        if (value instanceof UUID u) return quote(u.toString());
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?,?> map) {
            StringJoiner j = new StringJoiner(",", "{", "}");
            for (Map.Entry<?,?> e : map.entrySet()) {
                j.add(quote(String.valueOf(e.getKey())) + ":" + json(e.getValue()));
            }
            return j.toString();
        }
        if (value instanceof Iterable<?> items) {
            StringJoiner j = new StringJoiner(",", "[", "]");
            for (Object item : items) j.add(json(item));
            return j.toString();
        }
        return quote(String.valueOf(value));
    }

    private String quote(String s) {
        return "\"" + escapeJson(s) + "\"";
    }

    private String escapeJson(String s) {
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int)c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }

    private String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}