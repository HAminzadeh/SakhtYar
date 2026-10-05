package com.sakhtyar.knowledge.preparation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.UUID;

@Repository
public class KnowledgePreparationRunRepository {
    private final JdbcTemplate jdbc;

    public KnowledgePreparationRunRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public UUID create(UUID workflowId, UUID correlationId) {
        UUID runId = UUID.randomUUID();
        jdbc.update("""
            insert into knowledge_pipeline_run
              (id, workflow_id, correlation_id, pipeline_version, status, stage, summary)
            values (?, ?, ?, 'knowledge-preparation-v2', 'RUNNING', 'PREPARING', '{}'::jsonb)
            """, runId, workflowId, correlationId);
        return runId;
    }

    public void fail(UUID runId, String message) {
        jdbc.update("""
            update knowledge_pipeline_run
               set status='FAILED', stage='FAILED',
                   summary=coalesce(summary,'{}'::jsonb) || jsonb_build_object('error', ?)
             where id=?
            """, message, runId);
    }

    public Map<String,Object> get(UUID runId) {
        return jdbc.queryForMap("select * from knowledge_pipeline_run where id=?", runId);
    }
}
