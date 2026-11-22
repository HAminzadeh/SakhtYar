package com.sakhtyar.quality;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.ObjectMapper;

/**
 * Executes actual, READ-ONLY PostgreSQL and Spring MVC registry assertions.
 * This is NOT a business E2E runner: business-data writes must remain gated.
 * Only simulation_run, simulation_result and simulation_run_step are mutated.
 */
@Service
public class SimulationPreflightRunner implements ApplicationListener<ApplicationReadyEvent> {
    public record WorkItem(String scenarioId, List<String> tables, List<String> routes) {}
    private record StepOutcome(String state, String detail) {}
    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    private final RequestMappingHandlerMapping routes;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sakhtyar-simulation-preflight");
        t.setDaemon(true);
        return t;
    });
    private static final String SAFETY_REASON =
        "Business E2E is BLOCKED until REAL/TEST database isolation and outbound safeguards are verified.";

    public SimulationPreflightRunner(JdbcTemplate db, ObjectMapper mapper,
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping routes) {
        this.db=db; this.mapper=mapper; this.routes=routes;
    }

    /** No silent green after backend restart; interrupted jobs keep their evidence. */
    @Override public void onApplicationEvent(ApplicationReadyEvent event) {
        db.update("""
            update simulation_run_step set state='BLOCKED', detail='Interrupted by backend restart', finished_at=now()
            where state in ('QUEUED','RUNNING') and run_id in
             (select id from simulation_run where state in ('QUEUED','RUNNING'))
            """);
        db.update("""
            update simulation_result set state='BLOCKED', summary='Read-only preflight interrupted before completion', updated_at=now()
            where state='QUEUED' and run_id in
             (select id from simulation_run where state in ('QUEUED','RUNNING'))
            """);
        db.update("update simulation_run set state='INTERRUPTED',finished_at=now() where state in ('QUEUED','RUNNING')");
    }
    @jakarta.annotation.PreDestroy public void shutdown() { worker.shutdownNow(); }

    public void enqueue(UUID id, List<WorkItem> selected) {
        try { worker.execute(() -> execute(id, selected)); }
        catch(RejectedExecutionException ex) { interrupt(id, "Simulation worker unavailable"); }
    }

    private boolean hasTable(String table) {
        // Names are static, source-controlled scenario requirements.
        return Boolean.TRUE.equals(db.queryForObject(
            "select to_regclass(?) is not null",Boolean.class,"public."+table));
    }
    private boolean hasRoute(String route) {
        String[] split = route.split(" ",2);
        if(split.length != 2) return false;
        return routes.getHandlerMethods().keySet().stream().anyMatch(info ->
            info.getPatternValues().contains(split[1]) &&
            (info.getMethodsCondition().getMethods().isEmpty() ||
             info.getMethodsCondition().getMethods().stream().anyMatch(x -> x.name().equals(split[0]))));
    }
    private interface Probe { StepOutcome check() throws Exception; }

    private StepOutcome step(UUID runId, String scenarioId, int no, Probe probe) {
        db.update("""
            update simulation_run_step set state='RUNNING',started_at=now()
            where run_id=? and scenario_id=? and step_no=?
            """,runId,scenarioId,no);
        StepOutcome result;
        try { result = probe.check(); }
        catch(Exception ex) {
            // Do not expose SQL statements, request headers or other sensitive exception details.
            result = new StepOutcome("FAILED", "Probe failed: " + ex.getClass().getSimpleName());
        }
        db.update("""
            update simulation_run_step set state=?,detail=?,finished_at=now()
            where run_id=? and scenario_id=? and step_no=?
            """,result.state(),result.detail(),runId,scenarioId,no);
        return result;
    }
    private void execute(UUID id, List<WorkItem> items) {
        try {
            db.update("update simulation_run set state='RUNNING' where id=? and state='QUEUED'",id);
            for(WorkItem work:items) {
                String sid=work.scenarioId();
                StepOutcome connectivity=step(id,sid,1,()-> {
                    Integer one=db.queryForObject("select 1",Integer.class);
                    return new StepOutcome(Integer.valueOf(1).equals(one)?"PASSED":"FAILED",
                        Integer.valueOf(1).equals(one)?"Live PostgreSQL SELECT 1 passed":"Database connectivity check failed");
                });
                List<String> missingTables = new ArrayList<>();
                StepOutcome schema=step(id,sid,2,()-> {
                    for(String t:work.tables()) if(!hasTable(t)) missingTables.add(t);
                    return new StepOutcome(missingTables.isEmpty()?"PASSED":"FAILED",
                        missingTables.isEmpty()?"Required tables verified: "+work.tables().size():
                         "Missing tables: "+String.join(", ",missingTables));
                });
                List<String> missingRoutes = new ArrayList<>();
                StepOutcome api=step(id,sid,3,()-> {
                    for(String r:work.routes()) if(!hasRoute(r)) missingRoutes.add(r);
                    return new StepOutcome(missingRoutes.isEmpty()?"PASSED":"FAILED",
                        missingRoutes.isEmpty()?"Spring MVC route mappings verified: "+work.routes().size():
                         "Missing route mappings: "+String.join(", ",missingRoutes));
                });
                step(id,sid,4,()->new StepOutcome("BLOCKED",SAFETY_REASON));
                boolean ready="PASSED".equals(connectivity.state()) &&
                        "PASSED".equals(schema.state()) && "PASSED".equals(api.state());
                Map<String,Object> evidence=new LinkedHashMap<>();
                evidence.put("kind","READ_ONLY_PREFLIGHT");
                evidence.put("schemaAndRoutesReady",ready);
                evidence.put("tablesChecked",work.tables().size());
                evidence.put("routesChecked",work.routes().size());
                evidence.put("missingTables",missingTables);
                evidence.put("missingRoutes",missingRoutes);
                evidence.put("businessWorkflowExecuted",false);
                String json=mapper.writeValueAsString(evidence);
                db.update("""
                    update simulation_result set state=?,evidence=cast(? as jsonb),summary=?,updated_at=now()
                    where run_id=? and scenario_id=?
                    """,ready?"BLOCKED":"FAILED",json,
                    ready?"Read-only prerequisites passed; business E2E blocked by isolation gate":
                          "Read-only prerequisites failed; business E2E was not executed",id,sid);
            }
            db.update("update simulation_run set state='COMPLETED',finished_at=now() where id=?",id);
            db.update("""
                insert into simulation_audit(workspace_id,run_id,actor,action)
                select workspace_id,id,requested_by,'READ_ONLY_PREFLIGHT_COMPLETED' from simulation_run where id=?
                """,id);
        } catch(Exception ex) { interrupt(id,"Read-only preflight encountered an unexpected error"); }
    }
    private void interrupt(UUID id,String reason) {
        try {
            db.update("""
                update simulation_run_step set state='BLOCKED',detail=?,finished_at=now()
                where run_id=? and state in ('QUEUED','RUNNING')
                """,reason,id);
            db.update("""
                update simulation_result set state='BLOCKED',summary=?,updated_at=now()
                where run_id=? and state='QUEUED'
                """,reason,id);
            db.update("update simulation_run set state='INTERRUPTED',finished_at=now() where id=?",id);
        } catch(Exception ignored) { /* reconciliation will run on next successful startup */ }
    }
}
