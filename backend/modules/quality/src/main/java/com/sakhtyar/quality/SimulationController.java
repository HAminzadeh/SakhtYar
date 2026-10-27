package com.sakhtyar.quality;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Safe simulation control plane. General 24-scenario business E2E remains BLOCKED.
 * A separate explicit admin-only CORE fixture endpoint may write synthetic TEST
 * rows into four RLS-guarded tables only when the runtime DB role passes its gate.
 * Such a fixture is NOT a completed business E2E scenario.
 */
@RestController
@RequestMapping("/api/v1/admin/simulations")
public class SimulationController {
    public record WorkspaceRequest(String code, String title) {}
    public record RunRequest(UUID workspaceId, String requestKey, List<String> scenarioIds,
                             Instant simulatedClockAt) {}
    public record Scenario(String id, String phase, String title, String objective) {}
    private static final String SAFETY_GATE =
        "Business-data writes are disabled: full REAL/TEST row isolation, FK scope checks, " +
        "transaction context, outbox and object-storage isolation must first be verified.";
    private static final List<String> EVIDENCE_TABLES = List.of(
        "construction_case", "property", "property_owner", "case_document",
        "builder_profile", "project_builder", "contract_record",
        "material_price_observation", "cost_estimate", "construction_scenario",
        "urban_evaluation", "feasibility_assessment", "financial_analysis",
        "sensitivity_analysis", "analysis_snapshot", "knowledge_document",
        "knowledge_ai_import_item", "crawl_job", "market_observation", "notification_outbox"
    );
    // These are explicit read-only preflight contracts, NOT business E2E assertions.
    private static final Map<String,List<String>> REQUIRED_TABLES = Map.ofEntries(
      Map.entry("E2E-01",List.of("app_user","auth_session")),
      Map.entry("E2E-02",List.of("construction_case","construction_case_access")),
      Map.entry("E2E-03",List.of("knowledge_dataset_package")),
      Map.entry("E2E-04",List.of("urban_rule")),
      Map.entry("E2E-05",List.of("flyway_schema_history")),
      Map.entry("E2E-06",List.of("auth_session")),
      Map.entry("E2E-07",List.of("knowledge_dataset_package","knowledge_document")),
      Map.entry("E2E-08",List.of("knowledge_pipeline_run","knowledge_pipeline_checkpoint")),
      Map.entry("E2E-09",List.of("knowledge_legal_segment","knowledge_ocr_page_review")),
      Map.entry("E2E-10",List.of("knowledge_ai_import_item","knowledge_ai_import_review_event")),
      Map.entry("E2E-11",List.of("knowledge_rule_review","knowledge_rule_review_event")),
      Map.entry("E2E-12",List.of("knowledge_term","knowledge_relation")),
      Map.entry("E2E-13",List.of("crawl_source","crawl_job","crawled_document")),
      Map.entry("E2E-14",List.of("construction_case","property","property_owner","case_document")),
      Map.entry("E2E-15",List.of("builder_profile","project_builder")),
      Map.entry("E2E-16",List.of("material_price_observation","cost_estimate")),
      Map.entry("E2E-17",List.of("construction_scenario","scenario_cost_snapshot")),
      Map.entry("E2E-18",List.of("urban_evaluation","feasibility_assessment")),
      Map.entry("E2E-19",List.of("financial_analysis","financial_participation_allocation")),
      Map.entry("E2E-20",List.of("sensitivity_analysis","analysis_snapshot")),
      Map.entry("E2E-21",List.of("intelligence_workflow_execution")),
      Map.entry("E2E-22",List.of("construction_case")),
      Map.entry("E2E-23",List.of("monitoring_event","notification_outbox")),
      Map.entry("E2E-24",List.of("platform_config_version","platform_config_audit"))
    );
    private static final Map<String,List<String>> REQUIRED_ROUTES = Map.ofEntries(
      Map.entry("E2E-01",List.of("POST /api/v1/auth/login")),
      Map.entry("E2E-02",List.of("GET /api/v1/cases/{id}")),
      Map.entry("E2E-03",List.of("GET /api/v1/knowledge/admin/dataset-packages")),
      Map.entry("E2E-04",List.of("POST /api/v1/regulations/rules")),
      Map.entry("E2E-05",List.of("GET /api/v1/admin/quality/overview")),
      Map.entry("E2E-06",List.of("GET /api/v1/auth/csrf")),
      Map.entry("E2E-07",List.of("POST /api/v1/knowledge/admin/dataset-packages")),
      Map.entry("E2E-08",List.of("POST /api/v1/knowledge/admin/pipeline/start")),
      Map.entry("E2E-09",List.of("GET /api/v1/knowledge/admin/ai-lifecycle/documents")),
      Map.entry("E2E-10",List.of("POST /api/v1/knowledge/admin/ai-import/packages/{id}/items/{itemId}/review")),
      Map.entry("E2E-11",List.of("GET /api/v1/knowledge/admin/governance/pending")),
      Map.entry("E2E-12",List.of("GET /api/v1/knowledge/admin/ai-lifecycle/overview")),
      Map.entry("E2E-13",List.of("POST /api/v1/crawler/sources/{id}/run")),
      Map.entry("E2E-14",List.of("POST /api/v1/cases")),
      Map.entry("E2E-15",List.of("POST /api/v1/builders/cases/{caseId}/matches")),
      Map.entry("E2E-16",List.of("POST /api/v1/assemblies/{id}/calculate")),
      Map.entry("E2E-17",List.of("POST /api/v1/scenarios/{id}/calculate")),
      Map.entry("E2E-18",List.of("POST /api/v1/feasibility/assessments")),
      Map.entry("E2E-19",List.of("POST /api/v1/finance/analyses")),
      Map.entry("E2E-20",List.of("POST /api/v1/sensitivity/analyses")),
      Map.entry("E2E-21",List.of("POST /api/v1/intelligence/workflows/execute")),
      Map.entry("E2E-22",List.of("GET /api/v1/cases")),
      Map.entry("E2E-23",List.of("POST /api/v1/monitoring/evaluate")),
      Map.entry("E2E-24",List.of("GET /api/v1/admin/config"))
    );
    private final RequestMappingHandlerMapping routes;
    private final JdbcTemplate jdbc;
    private final Map<String,Scenario> catalog;
    private final String gitCommit;
    private final SimulationPreflightRunner preflightRunner;
    private final SimulationCoreFixtureService coreFixtureService;

    public SimulationController(JdbcTemplate jdbc, ObjectMapper mapper,
                   @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping routes,
                   SimulationPreflightRunner preflightRunner,
                   SimulationCoreFixtureService coreFixtureService,
                   @Value("${app.quality.git-commit:unknown}") String gitCommit) throws Exception {
        this.preflightRunner = preflightRunner;
        this.coreFixtureService = coreFixtureService;
        this.jdbc = jdbc;
        this.routes = routes;
        this.gitCommit = gitCommit;
        byte[] bytes = new ClassPathResource("simulation/scenarios.json").getInputStream().readAllBytes();
        JsonNode tree = mapper.readTree(new String(bytes, StandardCharsets.UTF_8));
        Map<String,Scenario> result = new LinkedHashMap<>();
        for(JsonNode n : tree) {
            Scenario s = new Scenario(n.path("id").asText(),n.path("phase").asText(),
                n.path("title").asText(),n.path("objective").asText());
            if(result.putIfAbsent(s.id(),s) != null)
                throw new IllegalStateException("Duplicate simulation scenario: " + s.id());
        }
        if(result.size() != 24) throw new IllegalStateException("Expected 24 simulation scenarios");
        this.catalog = Collections.unmodifiableMap(result);
    }
    private void manager(Authentication a) {
        if(a == null || !a.isAuthenticated() || a.getAuthorities() == null ||
            a.getAuthorities().stream().noneMatch(x -> "USER_MANAGE".equals(x.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Simulation administrator permission required");
        }
    }
    private Map<String,Object> one(String sql,Object... args) {
        List<Map<String,Object>> rows = jdbc.queryForList(sql,args);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Simulation record not found");
        return rows.get(0);
    }
    private Map<String,Object> workspace(UUID id) {
        return one("select * from simulation_workspace where id=?",id);
    }
    private boolean tableExists(String table) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "select to_regclass(?) is not null",Boolean.class,"public."+table));
    }
    @GetMapping("/t05-coverage")
    public List<Map<String,Object>> t05Coverage(Authentication a) {
        manager(a);
        List<Map<String,Object>> status = new ArrayList<>();
        for (Scenario scenario : catalog.values()) {
            Map<String,Object> item = new LinkedHashMap<>();
            item.put("scenarioId", scenario.id());
            item.put("phase", scenario.phase());
            item.put("title", scenario.title());
            item.put("businessE2eExecuted", false);
            item.put("status", "BLOCKED");
            item.put("coreFixtureAvailable", "E2E-14".equals(scenario.id()));
            item.put("note", "E2E-14".equals(scenario.id())
              ? "Separate RLS-guarded DB fixture for case/property/two owners; incomplete E2E-14"
              : "No automated business workflow or external side effects enabled");
            status.add(item);
        }
        return status;
    }

    @GetMapping("/core-fixture-readiness")
    public Map<String,Object> coreFixtureReadiness(Authentication a) {
        manager(a);
        return coreFixtureService.readiness();
    }

    public record CoreFixtureRequest(String requestKey) {}

    @PostMapping("/workspaces/{workspaceId}/core-fixtures")
    public Map<String,Object> createCoreFixture(@PathVariable UUID workspaceId,
          @RequestBody CoreFixtureRequest request, Authentication a) {
        manager(a);
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Request required");
        return coreFixtureService.create(workspaceId,request.requestKey(),a.getName());
    }

    @GetMapping("/workspaces/{workspaceId}/core-fixtures")
    public List<Map<String,Object>> coreFixtures(@PathVariable UUID workspaceId, Authentication a) {
        manager(a);
        workspace(workspaceId);
        return coreFixtureService.list(workspaceId);
    }

    @GetMapping("/catalog")
    public Collection<Scenario> catalog(Authentication a) { manager(a); return catalog.values(); }

    private boolean columnExists(String table,String column) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from information_schema.columns
            where table_schema='public' and table_name=? and column_name=?)
            """,Boolean.class,table,column));
    }
    private boolean routeExists(String declared) {
        String[] parts=declared.split(" ",2);
        if(parts.length!=2) return false;
        return routes.getHandlerMethods().keySet().stream().anyMatch(info ->
            info.getPatternValues().contains(parts[1]) &&
            (info.getMethodsCondition().getMethods().isEmpty() ||
             info.getMethodsCondition().getMethods().stream().anyMatch(m->m.name().equals(parts[0]))));
    }
    /** Pure read-only schema and route probes. No business controller is invoked. */
    @GetMapping("/scenario-checks")
    public List<Map<String,Object>> scenarioChecks(Authentication a) {
        manager(a);
        List<Map<String,Object>> checks=new ArrayList<>();
        for(Scenario scenario:catalog.values()) {
            List<String> tables=REQUIRED_TABLES.getOrDefault(scenario.id(),List.of());
            List<String> declaredRoutes=REQUIRED_ROUTES.getOrDefault(scenario.id(),List.of());
            List<String> missingTables=tables.stream().filter(t->!tableExists(t)).toList();
            List<String> missingRoutes=declaredRoutes.stream().filter(t->!routeExists(t)).toList();
            Map<String,Object> result=new LinkedHashMap<>();
            result.put("id",scenario.id());result.put("title",scenario.title());result.put("phase",scenario.phase());
            result.put("requiredTables",tables);result.put("missingTables",missingTables);
            result.put("requiredRoutes",declaredRoutes);result.put("missingRoutes",missingRoutes);
            result.put("schemaRoutesReady",missingTables.isEmpty()&&missingRoutes.isEmpty());
            result.put("businessE2EEnabled",false);
            checks.add(result);
        }
        return checks;
    }

    @GetMapping("/capabilities")
    public Map<String,Object> capabilities(Authentication a) {
        manager(a);
        var phaseStates = List.of(
            Map.of("phase","T01","title","Data-origin schema and guarded workspace registry","status","FOUNDATION"),
            Map.of("phase","T02","title","24-scenario declarations and safe persisted blocker records","status","FOUNDATION"),
            Map.of("phase","T03","title","Evidence export, DB counts, comparisons, and admin UI","status","FOUNDATION"),
            Map.of("phase","T04","title","AI/fault/replay/time controls: declared but not live-wired","status","PLANNED")
        );
        return Map.of("businessDataWriteEnabled",false,"isolationVerified",false,
            "sessionProfileSwitchEnabled",false,"outboundEffectsEnabled",false,
            "reason",SAFETY_GATE,"gitCommit",gitCommit,"phaseStatus",phaseStates);
    }
    @GetMapping("/readiness")
    public Map<String,Object> readiness(Authentication a) {
        manager(a);
        Map<String,Object> r = new LinkedHashMap<>();
        boolean tablesReady=tableExists("simulation_workspace") && tableExists("simulation_run");
        boolean migrationRecorded=tableExists("flyway_schema_history") && Boolean.TRUE.equals(
            jdbc.queryForObject("select exists(select 1 from flyway_schema_history where version='58' and success=true)",Boolean.class));
        r.put("migration58Installed",tablesReady && migrationRecorded);
        r.put("taggedBusinessTables",EVIDENCE_TABLES.stream().filter(t->tableExists(t) &&
            columnExists(t,"data_scope") && columnExists(t,"test_workspace_id") && columnExists(t,"test_run_id")).count());
        r.put("taggedBusinessTablesExpected",EVIDENCE_TABLES.size());
        r.put("rlsAndCrossScopeFkEnforcementVerified",false);
        r.put("outboundGuardVerified",false);
        r.put("allBusinessQueriesScopeGuarded",false);
        r.put("automatedTestBusinessWritesPermitted",false);
        r.put("gateReason",SAFETY_GATE);
        return r;
    }
    @GetMapping("/workspaces")
    public List<Map<String,Object>> workspaces(Authentication a) {
        manager(a);
        return jdbc.queryForList("select id,code,title,owner_username,state,created_at from simulation_workspace order by created_at desc limit 100");
    }
    @PostMapping("/workspaces")
    @Transactional
    public Map<String,Object> createWorkspace(@RequestBody WorkspaceRequest request, Authentication a) {
        manager(a);
        if(request == null || request.code() == null || !request.code().matches("[a-zA-Z0-9_-]{3,80}") ||
            request.title() == null || request.title().isBlank() || request.title().length() > 250)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valid code and title are required");
        UUID id = UUID.randomUUID();
        int inserted=jdbc.update("insert into simulation_workspace(id,code,title,owner_username) values(?,?,?,?) on conflict(code) do nothing",
            id,request.code(),request.title().trim(),a.getName());
        if(inserted != 1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Workspace code already exists");
        jdbc.update("insert into simulation_audit(workspace_id,actor,action) values(?,?,'WORKSPACE_CREATED')",id,a.getName());
        return workspace(id);
    }
    @GetMapping("/workspaces/{workspaceId}/evidence")
    public Map<String,Object> evidence(@PathVariable UUID workspaceId, Authentication a) {
        manager(a); workspace(workspaceId);
        Map<String,Long> counts=new LinkedHashMap<>();
        // This fixed allowlist is code-owned; no client-controlled SQL identifiers.
        for(String table:EVIDENCE_TABLES) {
            if(!tableExists(table)) continue;
            if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from information_schema.columns where table_schema='public' and table_name=? and column_name='test_workspace_id')",Boolean.class,table))) {
                counts.put(table,jdbc.queryForObject("select count(*) from public."+table+" where data_scope='TEST' and test_workspace_id=?",Long.class,workspaceId));
            }
        }
        return Map.of("workspaceId",workspaceId,"scope","TEST","rowsByTable",counts,
            "warning","Counts are observational. Isolation of general business APIs is not enabled.");
    }
    @PostMapping("/runs")
    @Transactional
    public Map<String,Object> begin(@RequestBody RunRequest req, Authentication a) {
        manager(a);
        if(req == null || req.workspaceId() == null || req.requestKey() == null ||
            !req.requestKey().matches("[A-Za-z0-9_-]{8,100}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"workspaceId and requestKey required");
        var w = workspace(req.workspaceId());
        if(!"ACTIVE".equals(w.get("state")))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Workspace is archived");
        LinkedHashSet<String> ids=new LinkedHashSet<>(req.scenarioIds()==null || req.scenarioIds().isEmpty()
            ?catalog.keySet():req.scenarioIds());
        if(ids.size()>24 || ids.isEmpty() || ids.stream().anyMatch(x->!catalog.containsKey(x)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid scenario selection");
        UUID id=UUID.randomUUID();
        int created=jdbc.update("""
            insert into simulation_run(id,workspace_id,request_key,requested_by,state,scenario_count,git_commit,simulated_clock_at,gate_reason)
            values(?,?,?,?,'QUEUED',?,?,?,?) on conflict(workspace_id,request_key) do nothing
            """,id,req.workspaceId(),req.requestKey(),a.getName(),ids.size(),gitCommit,req.simulatedClockAt(),SAFETY_GATE);
        if(created == 0) {
            UUID existing=jdbc.queryForObject("select id from simulation_run where workspace_id=? and request_key=?",
                UUID.class,req.workspaceId(),req.requestKey());
            return getRun(existing,a);
        }
        List<SimulationPreflightRunner.WorkItem> items=new ArrayList<>();
        String[] stepCodes={"DB_CONNECTIVITY","SCHEMA_TABLES","REGISTERED_API_ROUTES","BUSINESS_WRITE_GATE"};
        String[] stepTitles={"ارتباط با PostgreSQL","بررسی جداول موردنیاز","بررسی مسیرهای API","گیت امنیتی اجرای E2E واقعی"};
        for(String scenarioId:ids) {
            Scenario scenario=catalog.get(scenarioId);
            jdbc.update("""
                insert into simulation_result(run_id,scenario_id,title,phase,state,summary)
                values(?,?,?,?,'QUEUED','Awaiting real read-only database and route probes')
                """,id,scenario.id(),scenario.title(),scenario.phase());
            for(int n=0;n<stepCodes.length;n++) {
                jdbc.update("""
                    insert into simulation_run_step(run_id,scenario_id,step_no,step_code,title,state)
                    values(?,?,?,?,?,'QUEUED')
                    """,id,scenarioId,n+1,stepCodes[n],stepTitles[n]);
            }
            items.add(new SimulationPreflightRunner.WorkItem(scenarioId,
                REQUIRED_TABLES.getOrDefault(scenarioId,List.of()),
                REQUIRED_ROUTES.getOrDefault(scenarioId,List.of())));
        }
        jdbc.update("insert into simulation_audit(workspace_id,run_id,actor,action) values(?,?,?,'SAFE_PREFLIGHT_QUEUED')",
            req.workspaceId(),id,a.getName());
        // Enqueue AFTER transaction commit so the worker never races uncommitted rows.
        if(!TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("Simulation run must execute within an active transaction");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCommit() { preflightRunner.enqueue(id,List.copyOf(items)); }
        });
        return getRun(id,a);
    }
    @GetMapping("/runs")
    public List<Map<String,Object>> runs(Authentication a,@RequestParam(defaultValue="40") int limit) {
        manager(a);
        return jdbc.queryForList("select id,workspace_id,request_key,requested_by,requested_at,finished_at,state,scenario_count,git_commit from simulation_run order by requested_at desc limit ?",Math.max(1,Math.min(limit,100)));
    }
    @GetMapping("/runs/{id}")
    public Map<String,Object> getRun(@PathVariable UUID id,Authentication a) {
        manager(a);
        Map<String,Object> result=new LinkedHashMap<>(one("select * from simulation_run where id=?",id));
        result.put("results",jdbc.queryForList("select * from simulation_result where run_id=? order by scenario_id",id));
        var steps=jdbc.queryForList("""
            select run_id,scenario_id,step_no,step_code,title,state,detail,started_at,finished_at
            from simulation_run_step where run_id=? order by scenario_id,step_no
            """,id);
        result.put("steps",steps);
        int finished=(int)steps.stream().filter(x->Set.of("PASSED","FAILED","BLOCKED").contains(x.get("state"))).count();
        int total=steps.size();
        var progress=new LinkedHashMap<String,Object>();
        progress.put("totalSteps",total);
        progress.put("completedSteps",finished);
        progress.put("percent",total==0?0:(int)Math.round(finished*100.0/total));
        progress.put("kind","READ_ONLY_PREFLIGHT");
        progress.put("businessWorkflowsExecuted",false);
        progress.put("currentStep",steps.stream().filter(x->"RUNNING".equals(x.get("state"))).findFirst().orElse(null));
        result.put("progress",progress);
        return result;
    }
    @GetMapping("/runs/{id}/report")
    public Map<String,Object> report(@PathVariable UUID id,Authentication a) {
        manager(a);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("formatVersion","2.0");
        result.put("run",getRun(id,a));
        result.put("readiness",readiness(a));
        result.put("disclaimer","Read-only PostgreSQL/schema/API probes actually run and are persisted. BLOCKED E2E results do not demonstrate business-record writes or REAL/TEST isolation.");
        return result;
    }
    @GetMapping("/runs/{id}/compare")
    public Map<String,Object> compare(@PathVariable UUID id,@RequestParam UUID other,Authentication a) {
        manager(a);
        Map<String,Object> first=getRun(id,a),second=getRun(other,a);
        return Map.of("left",first,"right",second,"note","Compare blocker state and metadata only; no business E2E assertions performed.");
    }
    @GetMapping("/workspaces/{workspaceId}/audit")
    public List<Map<String,Object>> audit(@PathVariable UUID workspaceId,Authentication a) {
        manager(a);workspace(workspaceId);
        return jdbc.queryForList("select run_id,actor,action,created_at from simulation_audit where workspace_id=? order by created_at desc limit 100",workspaceId);
    }
}
