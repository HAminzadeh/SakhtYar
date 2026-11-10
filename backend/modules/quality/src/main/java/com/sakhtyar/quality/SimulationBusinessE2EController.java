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
    private final E2ETesterIdentityService testerIdentity;
    private final ScenarioExecutorRegistry registry;
    private final E2EEvidenceCollector evidenceCollector;
    private final SimulationWorkspaceAuthorizationService workspaceAuthorization;
    private static final ThreadLocal<E2EExecutionContext> ACTIVE_CTX = new ThreadLocal<>();
    // The provenance SHA comes from META-INF/build-info.properties embedded at build time.
    // A runtime environment variable alone is not a trustworthy source of binary provenance.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.boot.info.BuildProperties buildProperties;
    @org.springframework.beans.factory.annotation.Value("${app.dev-seed.enabled:false}")
    private boolean phase08DevSeedEnabled;
    private String sourceCommit() {
        String embedded = buildProperties == null ? null : buildProperties.get("sourceCommit");
        return embedded != null && embedded.matches("(?i)[0-9a-f]{40}") ? embedded : "UNVERIFIED";
    }
    // Bound the single-worker backlog instead of buffering an unbounded number of requests.
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(16), r -> {
            Thread thread=new Thread(r,"sakhtyar-business-e2e");thread.setDaemon(true);return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    private static final List<String> SCENARIOS=java.util.stream.IntStream.rangeClosed(1,24)
        .mapToObj(i -> "E2E-"+String.format(Locale.ROOT,"%02d",i)).toList();
    private static final List<String> STEPS=List.of(
        "بررسی گیت REAL/TEST و PostgreSQL", "ثبت پرونده، ملک و دو مالک در TEST",
        "بازیابی و کنترل مالکیت", "تأیید نامرئی‌بودن TEST در REAL");

    public SimulationBusinessE2EController(JdbcTemplate jdbc,SimulationCoreFixtureService fixtures,
            ObjectMapper mapper,SimulationScenarioChecks scenarioChecks,E2ETesterIdentityService testerIdentity,
            ScenarioExecutorRegistry registry,E2EEvidenceCollector evidenceCollector,
            SimulationWorkspaceAuthorizationService workspaceAuthorization) {
        this.jdbc=jdbc;this.fixtures=fixtures;this.mapper=mapper;this.scenarioChecks=scenarioChecks;
        this.testerIdentity=testerIdentity;this.registry=registry;this.evidenceCollector=evidenceCollector;
        this.workspaceAuthorization=workspaceAuthorization;
    }
    private void admin(Authentication auth) {
        if(auth==null || !auth.isAuthenticated() || auth.getAuthorities()==null ||
           auth.getAuthorities().stream().noneMatch(x->"USER_MANAGE".equals(x.getAuthority())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Simulation administrator required");
    }
    @Override @Transactional public void onApplicationEvent(ApplicationReadyEvent e) {
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
        return Map.of("implementedBusinessE2e",List.copyOf(registry.registeredIds()),"registeredScenarios",SCENARIOS,
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
        // Even administrators may operate only their own ACTIVE TEST workspaces.
        workspaceAuthorization.requireActiveOwner(req.workspaceId(), a);
        // Phase 08: verify ALL shared-DB TEST write proofs BEFORE recording a run,
        // creating the tester account, adding an E2E step, or calling any real API.
        // Phase 07 read-only diagnostics remain available when this is blocked.
        Map<String,Object> observedDbIdentity;
        try {
            observedDbIdentity=jdbc.queryForMap("""
                select current_user::text as db_current_user,
                       session_user::text as db_session_user,
                       current_setting('row_security')::text as row_security
                """);
        } catch(RuntimeException ex) { observedDbIdentity=Map.of(); }
        Map<String,Object> fixtureReadiness;
        try { fixtureReadiness=fixtures.readiness(); }
        catch(RuntimeException ex) { fixtureReadiness=Map.of(); }
        Phase08SafetyAdmission.Verdict safety=Phase08SafetyAdmission.assess(
            req.workspaceId(),fixtureReadiness,observedDbIdentity,phase08DevSeedEnabled,sourceCommit());
        if(!safety.authorized())
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Phase08 TEST isolation gate BLOCKED before any business E2E database write; "+
                "inspect GET /api/v1/admin/simulations/e2e/safety-readiness");
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
            java.util.Map<String,Object> previous=jdbc.queryForMap(
                "select requested_by,scenario_count from simulation_e2e_run where id=?", old);
            java.util.Set<String> previousIds=new java.util.HashSet<>(jdbc.queryForList(
                "select scenario_id from simulation_e2e_result where run_id=?",String.class,old));
            if (!String.valueOf(previous.get("requested_by")).equalsIgnoreCase(a.getName()) ||
                !previousIds.equals(new java.util.HashSet<>(ordered)))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency key reused for different request");
            return result(old,a);
        }
        E2ETesterIdentityService.Identity testAccount = testerIdentity.ensure();
        String traceId=UUID.randomUUID().toString();
        jdbc.update("update simulation_e2e_run set tester_user_id=?,tester_username=?,tester_identity_action=?,source_commit=?,trace_id=? where id=?",
            testAccount.id(),testAccount.username(),testAccount.action(),sourceCommit(),traceId,id);
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
                try { executor.execute(()->execute(id,req.workspaceId(),a.getName(),testAccount,traceId,List.copyOf(ordered))); }
                catch(RejectedExecutionException ex) { interrupt(id); }
            }
        });
        return result(id,a);
    }
    @GetMapping("/runs") public List<Map<String,Object>> runs(@RequestParam UUID workspaceId,Authentication a) {
        admin(a);
        workspaceAuthorization.requireActiveOwner(workspaceId,a);
        return jdbc.queryForList("select * from simulation_e2e_run where workspace_id=? order by requested_at desc limit 40",workspaceId);
    }
    @GetMapping("/runs/{id}") public Map<String,Object> result(@PathVariable UUID id,Authentication a) {
        admin(a);
        List<Map<String,Object>> rows=jdbc.queryForList("select * from simulation_e2e_run where id=?",id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"E2E run not found");
        workspaceAuthorization.requireActiveOwner((UUID)rows.get(0).get("workspace_id"),a);
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
        // Legacy four-table fixture PASSED is NOT complete business coverage.
        Long trueBusinessPasses=jdbc.queryForObject("""
            select count(*) from simulation_e2e_result where run_id=? and state='PASSED'
            and evidence->>'executionType'='FULL_BUSINESS_E2E'
            and evidence->>'businessWorkflowExecuted'='true'
            """,Long.class,id);
        out.put("businessScenarioPassedCount",trueBusinessPasses==null?0L:trueBusinessPasses);
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
        for (String required:plan.dependencies()) {
            Long complete=jdbc.queryForObject("""
                select count(*) from simulation_e2e_result
                where run_id=? and scenario_id=? and state='PASSED'
                  and evidence->>'executionType'='FULL_BUSINESS_E2E'
                  and evidence->>'businessWorkflowExecuted'='true'
                """,Long.class,runId,required);
            if (complete==null || complete!=1L) return false;
        }
        return true;
    }
    private void finish(UUID id,String code,String state,String summary,Map<String,Object> evidence) {
        try {
            E2EExecutionContext active=ACTIVE_CTX.get();
            String trace=active==null?"unknown":active.traceId();
            String json=mapper.writeValueAsString(evidenceCollector.collect(
                id.toString(),code,state,registry.executionType(code),
                active == null ? sourceCommit() : active.gitCommit(),trace,evidence));
            jdbc.update("""
                update simulation_e2e_result
                set state=?,summary=?,evidence=cast(? as jsonb),evidence_schema_version=1,finished_at=now()
                where run_id=? and scenario_id=?
                """,state,summary,json,id,code);
        } catch(Exception ex) { throw new IllegalStateException("Persisting E2E result failed",ex); }
    }
    private void execute(UUID id,UUID workspace,String actor,
                         E2ETesterIdentityService.Identity identity,String traceId,List<String> selected) {
        try {
            org.springframework.security.core.userdetails.UserDetails tester = testerIdentity.loadAuthorities();
            java.util.Set<String> authorities=tester.getAuthorities().stream()
                .map(x->x.getAuthority()).collect(java.util.stream.Collectors.toUnmodifiableSet());
            E2EExecutionContext executionContext=new E2EExecutionContext(
                id,workspace,actor,identity.id(),identity.username(),
                com.sakhtyar.identity.domain.UserRole.TESTER,authorities,sourceCommit(),
                new java.util.concurrent.atomic.AtomicBoolean(false),traceId);
            ACTIVE_CTX.set(executionContext);
            org.springframework.security.core.context.SecurityContext context =
                org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                tester, null, tester.getAuthorities()));
            org.springframework.security.core.context.SecurityContextHolder.setContext(context);
            jdbc.update("update simulation_e2e_run set state='RUNNING' where id=?",id);
            long workerDeadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(15);
            for(String code:selected) {
                executionContext.requireNotCancelled();
                if(System.nanoTime()>workerDeadline) throw new IllegalStateException("E2E worker deadline exceeded");
                jdbc.update("update simulation_e2e_result set state='RUNNING',started_at=now() where run_id=? and scenario_id=?",id,code);
                SimulationScenarioPlans.Plan plan=SimulationScenarioPlans.get(code);
                if(!dependenciesReady(id,plan)) {
                    blockUnfinished(id,code,"Required real business scenario did not PASS");
                    finish(id,code,"BLOCKED","Business dependency not verified",
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
                // Dispatch by a unique registry contract; unsupported cases remain BLOCKED.
                ScenarioExecutor.Outcome outcome=registry.execute(code,executionContext);
                if (!"PASSED".equals(outcome.state())) blockUnfinished(id,code,outcome.summary());
                finish(id,code,outcome.state(),outcome.summary(),outcome.evidence());
            }
            jdbc.update("update simulation_e2e_run set state='COMPLETED',finished_at=now() where id=?",id);
        } catch(Exception ex) { interrupt(id); }
        finally { ACTIVE_CTX.remove(); org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
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
