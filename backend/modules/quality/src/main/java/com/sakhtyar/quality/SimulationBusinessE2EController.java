package com.sakhtyar.quality;

import java.util.*;
import java.util.concurrent.*;
import org.springframework.context.ApplicationListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/**
 * True persisted E2E orchestrator, separate from read-only preflight.
 * Currently executes E2E-14 on exactly four guarded tables using the T05 core fixture.
 * All other scenarios are genuinely BLOCKED, never reported as PASSED.
 */
@RestController
@RequestMapping("/api/v1/admin/simulations/e2e")
public class SimulationBusinessE2EController implements ApplicationListener<ApplicationReadyEvent> {
    public record StartRequest(UUID workspaceId, String requestKey, List<String> scenarioIds) {}
    private final JdbcTemplate jdbc;
    private final SimulationCoreFixtureService fixtures;
    private final ObjectMapper mapper;
    private final SimulationScenarioChecks scenarioChecks;
    private final ExecutorService executor=Executors.newSingleThreadExecutor(r -> {
        Thread thread=new Thread(r,"sakhtyar-business-e2e");thread.setDaemon(true);return thread;
    });
    private static final List<String> SCENARIOS=java.util.stream.IntStream.rangeClosed(1,24)
        .mapToObj(i -> "E2E-"+String.format(Locale.ROOT,"%02d",i)).toList();
    private static final List<String> STEPS=List.of(
        "بررسی گیت REAL/TEST و PostgreSQL", "ثبت پرونده، ملک و دو مالک در TEST",
        "بازیابی و کنترل مالکیت", "تأیید نامرئی‌بودن TEST در REAL");

    public SimulationBusinessE2EController(JdbcTemplate jdbc,SimulationCoreFixtureService fixtures,
            ObjectMapper mapper,SimulationScenarioChecks scenarioChecks) {
        this.jdbc=jdbc;this.fixtures=fixtures;this.mapper=mapper;this.scenarioChecks=scenarioChecks;
    }
    private void admin(Authentication auth) {
        if(auth==null || !auth.isAuthenticated() || auth.getAuthorities()==null ||
           auth.getAuthorities().stream().noneMatch(x->"USER_MANAGE".equals(x.getAuthority())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Simulation administrator required");
    }
    @Override public void onApplicationEvent(ApplicationReadyEvent e) {
        jdbc.update("""
            update simulation_e2e_result set state='BLOCKED',summary='Interrupted by backend restart',finished_at=now()
            where state in ('QUEUED','RUNNING') and run_id in
              (select id from simulation_e2e_run where state in ('QUEUED','RUNNING'))
            """);
        jdbc.update("""
            update simulation_e2e_step set state='BLOCKED',detail='Interrupted by backend restart',finished_at=now()
            where state in ('QUEUED','RUNNING') and run_id in
              (select id from simulation_e2e_run where state in ('QUEUED','RUNNING'))
            """);
        jdbc.update("update simulation_e2e_run set state='INTERRUPTED',finished_at=now() where state in ('QUEUED','RUNNING')");
    }
    @jakarta.annotation.PreDestroy public void shutdown() { executor.shutdownNow(); }

    /** 24 detailed plans including topologically ordered prerequisites and safe execution boundaries. */
    @GetMapping("/plans") public Collection<SimulationScenarioPlans.Plan> plans(Authentication a) {
        admin(a); return SimulationScenarioPlans.all();
    }
    @GetMapping("/coverage") public Map<String,Object> coverage(Authentication a) {
        admin(a);
        return Map.of("implementedBusinessE2e",List.of("E2E-14"),"registeredScenarios",SCENARIOS,
            "completeBusinessE2eCount",0,"limitedBusinessE2eCount",1,"totalBusinessE2eCount",24,
            "note","Zero complete business workflows. E2E-14 has a guarded four-table fixture, not document upload or public API lifecycle. Other 23 require dedicated isolated implementations.");
    }
    @PostMapping("/runs") @Transactional
    public Map<String,Object> start(@RequestBody StartRequest req,Authentication a) {
        admin(a);
        if(req==null || req.workspaceId()==null || req.requestKey()==null ||
            !req.requestKey().matches("[A-Za-z0-9_-]{8,100}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"workspaceId and requestKey required");
        List<String> chosen=(req.scenarioIds()==null || req.scenarioIds().isEmpty())?SCENARIOS:req.scenarioIds();
        LinkedHashSet<String> unique=new LinkedHashSet<>(chosen);
        if(unique.size()<1 || unique.size()>24 || unique.stream().anyMatch(x->!SCENARIOS.contains(x)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid scenario IDs");
        // Expand dependencies and order them BEFORE business operations; never skip prerequisite probes.
        List<String> ordered=SimulationScenarioPlans.orderedWithPrerequisites(unique);
        Boolean active=jdbc.queryForObject("select exists(select 1 from simulation_workspace where id=? and state='ACTIVE')",
            Boolean.class,req.workspaceId());
        if(!Boolean.TRUE.equals(active)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Active TEST workspace required");
        UUID id=UUID.randomUUID();
        int created=jdbc.update("""
            insert into simulation_e2e_run(id,workspace_id,request_key,requested_by,scenario_count)
            values(?,?,?,?,?) on conflict(workspace_id,request_key) do nothing
            """,id,req.workspaceId(),req.requestKey(),a.getName(),ordered.size());
        if(created==0) {
            UUID old=jdbc.queryForObject("select id from simulation_e2e_run where workspace_id=? and request_key=?",
                UUID.class,req.workspaceId(),req.requestKey());
            return result(old,a);
        }
        for(String code:ordered) {
            jdbc.update("insert into simulation_e2e_result(run_id,scenario_id) values(?,?)",id,code);
            SimulationScenarioPlans.Plan plan=SimulationScenarioPlans.get(code);
            for(int i=0;i<plan.preflightSteps().size();i++)
                jdbc.update("""
                    insert into simulation_e2e_step(run_id,scenario_id,step_no,title)
                    values(?,?,?,?)
                    """,id,code,i+1,plan.preflightSteps().get(i));
            if(!"E2E-14".equals(code)) jdbc.update("""
                insert into simulation_e2e_step(run_id,scenario_id,step_no,title)
                values(?,?,5,'ممیزی واقعی سلامت دامنه، روابط و ایزوله‌سازی')
                """,id,code);
            if("E2E-14".equals(code)) for(int i=0;i<STEPS.size();i++)
                jdbc.update("""
                    insert into simulation_e2e_step(run_id,scenario_id,step_no,title)
                    values(?,?,?,?)
                    """,id,code,i+5,STEPS.get(i));
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { executor.execute(()->execute(id,req.workspaceId(),a.getName(),List.copyOf(ordered))); }
                catch(RejectedExecutionException ex) { interrupt(id); }
            }
        });
        return result(id,a);
    }
    @GetMapping("/runs") public List<Map<String,Object>> runs(@RequestParam UUID workspaceId,Authentication a) {
        admin(a);
        return jdbc.queryForList("select * from simulation_e2e_run where workspace_id=? order by requested_at desc limit 40",workspaceId);
    }
    @GetMapping("/runs/{id}") public Map<String,Object> result(@PathVariable UUID id,Authentication a) {
        admin(a);
        List<Map<String,Object>> rows=jdbc.queryForList("select * from simulation_e2e_run where id=?",id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"E2E run not found");
        Map<String,Object> out=new LinkedHashMap<>(rows.get(0));
        List<Map<String,Object>> results=jdbc.queryForList("select * from simulation_e2e_result where run_id=? order by scenario_id",id);
        List<Map<String,Object>> steps=jdbc.queryForList("select * from simulation_e2e_step where run_id=? order by scenario_id,step_no",id);
        out.put("results",results);out.put("steps",steps);
        out.put("executionKind","PREREQUISITES_THEN_GATED_BUSINESS_E2E");
        long done=results.stream().filter(r->List.of("PASSED","FAILED","BLOCKED").contains(r.get("state"))).count();
        out.put("completedScenarios",done);
        long completedSteps=steps.stream().filter(x->List.of("PASSED","FAILED","BLOCKED").contains(x.get("state"))).count();
        out.put("totalSteps",steps.size());
        out.put("completedSteps",completedSteps);
        out.put("progressPercent",steps.isEmpty()?0L:Math.round(100.0*completedSteps/steps.size()));
        out.put("scenarioProgressPercent",Math.round(done*100.0/((Number)out.get("scenario_count")).doubleValue()));
        out.put("businessScenarioPassedCount",results.stream().filter(x->"PASSED".equals(x.get("state"))).count());
        out.put("businessScenarioBlockedCount",results.stream().filter(x->"BLOCKED".equals(x.get("state"))).count());
        out.put("businessScenarioFailedCount",results.stream().filter(x->"FAILED".equals(x.get("state"))).count());
        return out;
    }
    private void preflightStep(UUID runId,String scenario,int index,String state,String detail) {
        jdbc.update("""
            update simulation_e2e_step set state=?,detail=?,
              started_at=coalesce(started_at,now()),
              finished_at=case when ?='RUNNING' then null else now() end
            where run_id=? and scenario_id=? and step_no=?
            """,state,detail,state,runId,scenario,index);
    }
    private void blockUnfinished(UUID runId,String scenario,String reason) {
        jdbc.update("""
            update simulation_e2e_step set state='BLOCKED',detail=?,finished_at=now()
            where run_id=? and scenario_id=? and state='QUEUED'
            """,reason,runId,scenario);
    }
    private boolean dependenciesReady(UUID runId,SimulationScenarioPlans.Plan plan) {
        for(String required:plan.dependencies()) {
            // A dependency's non-mutating integrity prerequisites must have passed;
            // this does NOT imply its real-world business workflow passed.
            Long succeeded=jdbc.queryForObject("""
                select count(*) from simulation_e2e_step
                where run_id=? and scenario_id=? and step_no in (1,2,3) and state='PASSED'
                """,Long.class,runId,required);
            if(succeeded==null || succeeded!=3L) return false;
        }
        return true;
    }
    private void advance(UUID id,int index,String state,String detail) {
        jdbc.update("""
            update simulation_e2e_step set state=?,detail=?,
              started_at=coalesce(started_at,now()),finished_at=case when ?='RUNNING' then null else now() end
            where run_id=? and scenario_id='E2E-14' and step_no=?
            """,state,detail,state,id,index+4);
    }
    private void finish(UUID id,String code,String state,String summary,Map<String,Object> evidence) {
        try {
            String json=mapper.writeValueAsString(evidence);
            jdbc.update("""
                update simulation_e2e_result
                set state=?,summary=?,evidence=cast(? as jsonb),finished_at=now()
                where run_id=? and scenario_id=?
                """,state,summary,json,id,code);
        } catch(Exception ex) { throw new IllegalStateException("Persisting E2E result failed",ex); }
    }
    private void execute(UUID id,UUID workspace,String actor,List<String> selected) {
        try {
            jdbc.update("update simulation_e2e_run set state='RUNNING' where id=?",id);
            for(String code:selected) {
                jdbc.update("update simulation_e2e_result set state='RUNNING',started_at=now() where run_id=? and scenario_id=?",id,code);
                SimulationScenarioPlans.Plan plan=SimulationScenarioPlans.get(code);
                if(!dependenciesReady(id,plan)) {
                    blockUnfinished(id,code,"Earlier prerequisite integrity checks did not pass");
                    finish(id,code,"BLOCKED","Dependent prerequisite verification failed",
                        Map.of("kind","PREREQUISITE_NOT_MET","dependsOn",plan.dependencies(),"businessWorkflowExecuted",false));
                    continue;
                }
                List<Map<String,Object>> verified=new ArrayList<>();
                boolean prerequisitesPassed=true;
                for(int index=1;index<=4;index++) {
                    preflightStep(id,code,index,"RUNNING","Checking prerequisite "+index);
                    SimulationScenarioChecks.Outcome outcome=scenarioChecks.check(plan,index);
                    preflightStep(id,code,index,outcome.status(),outcome.detail());
                    verified.add(Map.of("step",index,"status",outcome.status(),"detail",outcome.detail(),"evidence",outcome.evidence()));
                    if(!"PASSED".equals(outcome.status())) {
                        // A failed safety gate must not hide independent, read-only domain
                        // assertions. Run them before marking remaining business work BLOCKED.
                        if(index==4 && !"E2E-14".equals(code)) {
                            preflightStep(id,code,5,"RUNNING","Running live domain/schema isolation audit");
                            SimulationScenarioChecks.Outcome audit=scenarioChecks.domainIntegrity(plan);
                            preflightStep(id,code,5,audit.status(),audit.detail());
                            verified.add(Map.of("step",5,"status",audit.status(),"detail",audit.detail(),
                                "evidence",audit.evidence()));
                        }
                        prerequisitesPassed=false;
                        blockUnfinished(id,code,"Stopped after failed or blocked prerequisite "+index);
                        String finalState="FAILED".equals(outcome.status())?"FAILED":"BLOCKED";
                        finish(id,code,finalState,"Prerequisite "+index+": "+outcome.detail(),
                            Map.of("kind","PREREQUISITE_CHECK","prerequisites",verified,
                                   "businessWorkflowExecuted",false));
                        break;
                    }
                }
                if(!prerequisitesPassed) continue;
                if(!"E2E-14".equals(code)) {
                    // Defensive fail-closed fallback: currently every other business executor
                    // is deliberately blocked by SimulationScenarioChecks.gate().
                    finish(id,code,"BLOCKED","No verified isolated business workflow implementation",
                        Map.of("kind","UNIMPLEMENTED_BUSINESS_WORKFLOW","businessWorkflowExecuted",false));
                    continue;
                }
                executeCase(id,workspace,actor);
            }
            jdbc.update("update simulation_e2e_run set state='COMPLETED',finished_at=now() where id=?",id);
        } catch(Exception ex) { interrupt(id); }
    }
    private void executeCase(UUID id,UUID workspace,String actor) {
        Map<String,Object> gate;
        try { advance(id,1,"RUNNING","Checking DB role and FORCE RLS");gate=fixtures.readiness(); }
        catch(Exception ex) {
            blockRemaining(id,1,"Readiness error: "+ex.getClass().getSimpleName());
            finish(id,"E2E-14","FAILED","Readiness verification failed: "+ex.getClass().getSimpleName(),
                Map.of("businessWorkflowExecuted",false));
            return;
        }
        if(!Boolean.TRUE.equals(gate.get("coreFixturePermitted"))) {
            String detail="Safety gate: "+gate.getOrDefault("blockers",List.of("Core TEST isolation not ready"));
            blockRemaining(id,1,detail);
            finish(id,"E2E-14","BLOCKED",detail,Map.of("businessWorkflowExecuted",false,"gate",gate));
            return;
        }
        advance(id,1,"PASSED","Restricted DB role and FORCE RLS verified");
        Map<String,Object> fixture;
        try {
            advance(id,2,"RUNNING","Inserting TEST-scoped case/property/owners");
            String key="e2e14_"+id.toString().replace("-","");
            fixture=fixtures.create(workspace,key,actor);
            advance(id,2,"PASSED","Transactional fixture committed");
            advance(id,3,"RUNNING","Reloading committed TEST fixture");
            UUID caseId=(UUID)fixture.get("case_id");
            List<Map<String,Object>> fresh=fixtures.list(workspace);
            boolean found=fresh.stream().anyMatch(row->Objects.equals(caseId,row.get("case_id")) &&
                row.get("owner_count") instanceof Number count && count.longValue()==2L);
            if(!found) throw new IllegalStateException("TEST fixture not readable or owner count mismatch");
            advance(id,3,"PASSED","Committed TEST case and two owners retrieved");
            advance(id,4,"PASSED","REAL-mode invisibility checked transactionally by SimulationCoreFixtureService.create");
            finish(id,"E2E-14","PASSED","Limited 4-table TEST fixture committed and REAL isolation asserted",
                Map.of("businessWorkflowExecuted",true,"kind","CORE_FOUR_TABLE_FIXTURE",
                    "caseId",caseId.toString(),"workspaceId",workspace.toString(),
                    "scope","TEST","note","Not a complete API/document/business-lifecycle E2E"));
        } catch(Exception ex) {
            for(int n=2;n<=4;n++) jdbc.update("""
                update simulation_e2e_step set state='BLOCKED',detail='Failed/aborted before completion',finished_at=now()
                where run_id=? and scenario_id='E2E-14' and step_no=? and state in ('QUEUED','RUNNING')
                """,id,n+4);
            finish(id,"E2E-14","FAILED","Fixture execution failed: "+ex.getClass().getSimpleName(),
                Map.of("businessWorkflowExecuted",false,"errorType",ex.getClass().getSimpleName()));
        }
    }
    private void blockRemaining(UUID id,int from,String detail) {
        jdbc.update("""
            update simulation_e2e_step set state='BLOCKED',detail=?,finished_at=now()
            where run_id=? and scenario_id='E2E-14' and step_no>=?
            """,detail,id,from+4);
    }
    private void interrupt(UUID id) {
        try {
            jdbc.update("""
                update simulation_e2e_step set state='BLOCKED',detail='Worker interrupted',finished_at=now()
                where run_id=? and state in ('QUEUED','RUNNING')
                """,id);
            jdbc.update("""
                update simulation_e2e_result set state='BLOCKED',summary='Worker interrupted',finished_at=now()
                where run_id=? and state in ('QUEUED','RUNNING')
                """,id);
            jdbc.update("update simulation_e2e_run set state='INTERRUPTED',finished_at=now() where id=?",id);
        } catch(Exception ignored) { /* DB-recovery on next startup */ }
    }
}
