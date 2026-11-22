package com.sakhtyar.knowledge.preparation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.UUID;

@Repository
public class KnowledgePreparationRunRepository {
    private static final String PIPELINE_VERSION = "knowledge-preparation-v3";
    private final JdbcTemplate jdbc;

    public KnowledgePreparationRunRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID create(UUID workflowId, UUID correlationId) {
        UUID runId = UUID.randomUUID();
        String runCode = "KP-" + runId.toString().substring(0, 8);
        jdbc.update("""
            insert into knowledge_pipeline_run
              (id, run_code, mode, status, stage, command_text, runbook_version,
               started_at, workflow_id, correlation_id, pipeline_version, statistics)
            values (?, ?, 'INCREMENTAL', 'RUNNING', 'PREPARING',
                    'Java orchestrated Knowledge Preparation',
                    'knowledge-preparation-v3', now(), ?, ?, ?, '{}'::jsonb)
            """, runId, runCode, workflowId, correlationId, PIPELINE_VERSION);
        return runId;
    }

    public void fail(UUID runId, String message) {
        jdbc.update("""
            update knowledge_pipeline_run
               set status='FAILED', stage='FAILED', error_message=?, finished_at=now()
             where id=?
            """, abbreviate(message), runId);
    }

    public Map<String,Object> get(UUID runId) {
        return jdbc.queryForMap("select * from knowledge_pipeline_run where id=?", runId);
    }

    private static String abbreviate(String value) {
        if (value == null) return null;
        return value.length() <= 8000 ? value : value.substring(0, 8000);
    }
}
