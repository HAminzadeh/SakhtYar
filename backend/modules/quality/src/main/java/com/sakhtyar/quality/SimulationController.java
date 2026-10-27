package com.sakhtyar.quality;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Safe simulation control plane. This controller intentionally NEVER writes
 * business tables until an audited, end-to-end database isolation mechanism
 * exists. Its blocked results are NOT successful E2E tests.
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
    private final JdbcTemplate jdbc;
    private final Map<String,Scenario> catalog;
    private final String gitCommit;

    public SimulationController(JdbcTemplate jdbc, ObjectMapper mapper,
                   @Value("${app.quality.git-commit:unknown}") String gitCommit) throws Exception {
        this.jdbc = jdbc;
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
    @GetMapping("/catalog")
    public Collection<Scenario> catalog(Authentication a) { manager(a); return catalog.values(); }

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
        r.put("migration58Installed",tableExists("simulation_workspace") && tableExists("simulation_run"));
        r.put("taggedBusinessTables",EVIDENCE_TABLES.stream().filter(this::tableExists).count());
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
        LinkedHashSet<String> ids=new LinkedHashSet<>(req.scenarioIds()==null||req.scenarioIds().isEmpty()?catalog.keySet():req.scenarioIds());
        if(ids.size()>24 || ids.isEmpty() || ids.stream().anyMatch(x -> !catalog.containsKey(x)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid scenario selection");
        UUID id=UUID.randomUUID();
        int created=jdbc.update("""
            insert into simulation_run(id,workspace_id,request_key,requested_by,state,scenario_count,git_commit,simulated_clock_at,gate_reason)
            values(?,?,?,?,'BLOCKED',?,?,?,?) on conflict(workspace_id,request_key) do nothing
            """,id,req.workspaceId(),req.requestKey(),a.getName(),ids.size(),gitCommit,req.simulatedClockAt(),SAFETY_GATE);
        if(created == 0) {
            UUID existing=jdbc.queryForObject("select id from simulation_run where workspace_id=? and request_key=?",UUID.class,req.workspaceId(),req.requestKey());
            return getRun(existing,a);
        }
        for(String scenarioId:ids) {
            Scenario s=catalog.get(scenarioId);
            jdbc.update("""
                insert into simulation_result(run_id,scenario_id,title,phase,state,summary)
                values(?,?,?,?,'BLOCKED',?)
                """,id,s.id(),s.title(),s.phase(),SAFETY_GATE);
        }
        jdbc.update("update simulation_run set finished_at=now() where id=?",id);
        jdbc.update("insert into simulation_audit(workspace_id,run_id,actor,action) values(?,?,?,'SAFE_PREFLIGHT_RECORDED')",req.workspaceId(),id,a.getName());
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
        return result;
    }
    @GetMapping("/runs/{id}/report")
    public Map<String,Object> report(@PathVariable UUID id,Authentication a) {
        manager(a);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("formatVersion","1.0");
        result.put("run",getRun(id,a));
        result.put("readiness",readiness(a));
        result.put("disclaimer","BLOCKED results do not demonstrate E2E execution, business row persistence, or REAL/TEST isolation.");
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
