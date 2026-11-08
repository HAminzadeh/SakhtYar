package com.sakhtyar.quality;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Independently runs 24 source-defined diagnostic executors in the existing TEST workspace.
 * Only read-only catalog, MVC and PostgreSQL metadata probes plus in-memory Phase 02
 * fake adapters are used. DOES NOT alter the guarded BUSINESS E2E orchestrator or
 * claim that any business scenario passed. Reports are volatile and exportable as JSON.
 */
@RestController
@RequestMapping("/api/v1/admin/simulations/e2e/diagnostics")
public final class Phase07DiagnosticCenterController {
    public record Request(UUID workspaceId, List<String> scenarioIds) {}
    private record Stored(UUID workspace, String owner, Instant created, Map<String,Object> report) {}
    private final SimulationScenarioChecks checks;
    private final SimulationWorkspaceAuthorizationService workspaceAuthorization;
    private final Map<String,Phase07DiagnosticExecutor> executors;
    private final ConcurrentHashMap<UUID,Stored> reports = new ConcurrentHashMap<>();
    private final Object lock = new Object();
    @Autowired(required=false) private BuildProperties buildProperties;

    public Phase07DiagnosticCenterController(SimulationScenarioChecks checks,
            SimulationWorkspaceAuthorizationService workspaceAuthorization,
            List<Phase07DiagnosticExecutor> discovered) {
        this.checks = checks;
        this.workspaceAuthorization = workspaceAuthorization;
        Map<String,Phase07DiagnosticExecutor> mapped = new HashMap<>();
        for (Phase07DiagnosticExecutor diagnostic : discovered) {
            if (diagnostic==null || !diagnostic.scenarioId().matches("E2E-(0[1-9]|1[0-9]|2[0-4])") ||
                mapped.putIfAbsent(diagnostic.scenarioId(), diagnostic)!=null)
                throw new IllegalStateException("Invalid or duplicate Phase07 diagnostic executor");
        }
        if (mapped.size()!=24 || !mapped.keySet().equals(
            SimulationScenarioPlans.all().stream().map(SimulationScenarioPlans.Plan::id)
            .collect(java.util.stream.Collectors.toSet())))
            throw new IllegalStateException("Phase07 diagnostic coverage must be 24/24");
        this.executors=Map.copyOf(mapped);
    }

    private void admin(Authentication auth) {
        if (auth==null || !auth.isAuthenticated() || auth.getAuthorities()==null ||
            auth.getAuthorities().stream().noneMatch(a->"USER_MANAGE".equals(a.getAuthority())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Simulation administrator required");
    }

    @GetMapping("/coverage") public Map<String,Object> coverage(Authentication auth) {
        admin(auth);
        return Map.of("diagnosticExecutors",executors.keySet().stream().sorted().toList(),
            "diagnosticExecutorCount",executors.size(),"fullBusinessExecutorsProven",0,
            "mode","READ_ONLY_AND_PHASE02_FAKE_ONLY",
            "note","24 runnable diagnostics. NOT 24 full business E2E workflows; existing REAL/TEST gates unchanged.");
    }

    @PostMapping("/runs") public Map<String,Object> run(@RequestBody Request request, Authentication auth) {
        admin(auth);
        if(request==null || request.workspaceId()==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Active TEST workspace required");
        workspaceAuthorization.requireActiveOwner(request.workspaceId(),auth);
        List<String> selected = request.scenarioIds()==null || request.scenarioIds().isEmpty()
            ? executors.keySet().stream().sorted().toList() : request.scenarioIds();
        Set<String> unique = new LinkedHashSet<>(selected);
        if(unique.isEmpty() || unique.size()>24 || unique.size()!=selected.size() ||
            unique.stream().anyMatch(x->x==null || !executors.containsKey(x)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid or duplicate scenario IDs");

        UUID runId=UUID.randomUUID();
        Instant started=Instant.now();
        List<Map<String,Object>> results=new ArrayList<>();
        long successfulReadOnlyChecks=0L, totalChecks=0L;
        for(String id:unique.stream().sorted().toList()) {
            SimulationScenarioPlans.Plan plan=SimulationScenarioPlans.get(id);
            List<Map<String,Object>> steps=new ArrayList<>();
            List<String> blockers=new ArrayList<>();
            int passing=0;
            for(int i=1;i<=4;i++) {
                SimulationScenarioChecks.Outcome probe=checks.check(plan,i);
                steps.add(step(i, switch(i) { case 1 -> "POSTGRES_CONNECTIVITY";
                    case 2 -> "REQUIRED_TABLES"; case 3 -> "MVC_ROUTE_REGISTRATION";
                    default -> "EXISTING_TEST_SAFETY_GATE"; },probe));
                if("PASSED".equals(probe.status())) passing++;
                else blockers.add("Step "+i+": "+probe.detail());
            }
            SimulationScenarioChecks.Outcome domain=checks.domainIntegrity(plan);
            steps.add(step(5,"DOMAIN_SCHEMA_AND_SCOPE_AUDIT",domain));
            if("PASSED".equals(domain.status())) passing++;
            else blockers.add("Step 5: "+domain.detail());
            Map<String,Object> fake=Map.of();
            try {
                fake=executors.get(id).execute(request.workspaceId(),runId);
                steps.add(Map.of("step",6,"name","PHASE02_OFFLINE_EXECUTOR","state","PASSED",
                    "detail","Scoped fake storage, deterministic fixture IDs and real-outbound denial were exercised",
                    "evidence",fake));
                passing++;
            } catch(RuntimeException ex) {
                blockers.add("Step 6: synthetic contract failed: "+ex.getClass().getSimpleName());
                steps.add(Map.of("step",6,"name","PHASE02_OFFLINE_EXECUTOR","state","FAILED",
                    "detail",ex.getClass().getSimpleName()));
            }
            // Never infer a business pass from metadata, mocks or limited four-table fixtures.
            blockers.add("Full business E2E not verified: "+plan.businessSteps());
            blockers.add("Missing authenticated positive/negative HTTP, restricted TEST DB, browser and hashed artifacts");
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("scenario_id",id);item.put("phase",plan.phase());item.put("title",plan.title());
            item.put("state","BLOCKED"); item.put("diagnosticState",passing==6?"OFFLINE_CHECKS_COMPLETE":"REQUIRES_WORK");
            item.put("safeChecksPassed",passing);item.put("safeChecksTotal",6);
            item.put("dependencies",plan.dependencies());
            item.put("requiredTables",plan.requiredTables()); item.put("requiredRoutes",plan.requiredRoutes());
            item.put("steps",List.copyOf(steps));item.put("blockers",List.copyOf(blockers));
            item.put("executorEvidence",fake);
            item.put("businessWorkflowExecuted",false);
            results.add(Map.copyOf(item));
            successfulReadOnlyChecks+=passing;totalChecks+=6;
        }
        Map<String,Object> report=new LinkedHashMap<>();
        report.put("id",runId.toString());report.put("workspace_id",request.workspaceId().toString());
        report.put("requested_by",auth.getName());report.put("state","COMPLETED");
        report.put("mode","SAFE_INDEPENDENT_DIAGNOSTIC_NOT_BUSINESS_E2E");
        report.put("started_at",started.toString());report.put("finished_at",Instant.now().toString());
        report.put("source_commit",sourceCommit());report.put("scenario_count",results.size());
        report.put("diagnosticExecutorCount",executors.size());
        report.put("safeChecksPassed",successfulReadOnlyChecks);report.put("safeChecksTotal",totalChecks);
        report.put("businessScenarioPassedCount",0);report.put("businessScenarioBlockedCount",results.size());
        report.put("businessWorkflowsExecuted",false);report.put("databaseCloneCreated",false);
        report.put("businessDatabaseWrites",false);report.put("outboundNetworkRequests",0);
        report.put("results",List.copyOf(results));
        report.put("caution","Metadata queries used the application's existing JDBC identity; this is NOT proof of restricted TEST login or RLS isolation.");
        Map<String,Object> frozen=java.util.Collections.unmodifiableMap(new LinkedHashMap<>(report));
        synchronized(lock) {
            Instant cutoff=Instant.now().minusSeconds(3600);
            reports.entrySet().removeIf(e->e.getValue().created().isBefore(cutoff));
            while(reports.size()>=40) {
                UUID oldest=reports.entrySet().stream().min(java.util.Comparator.comparing(e->e.getValue().created()))
                    .orElseThrow().getKey();
                reports.remove(oldest);
            }
            reports.put(runId,new Stored(request.workspaceId(),auth.getName(),Instant.now(),frozen));
        }
        return frozen;
    }

    @GetMapping("/runs/{id}") public Map<String,Object> get(@PathVariable UUID id, Authentication auth) {
        admin(auth);
        Stored stored=reports.get(id);
        if(stored==null || stored.created().isBefore(Instant.now().minusSeconds(3600)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Diagnostic report unavailable or expired");
        workspaceAuthorization.requireActiveOwner(stored.workspace(),auth);
        if(!stored.owner().equalsIgnoreCase(auth.getName()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Diagnostic report unavailable");
        return stored.report();
    }

    private Map<String,Object> step(int number,String name,SimulationScenarioChecks.Outcome probe) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("step",number);result.put("name",name);result.put("state",probe.status());
        result.put("detail",probe.detail());result.put("evidence",probe.evidence());
        return Map.copyOf(result);
    }

    private String sourceCommit() {
        String sha=buildProperties==null?null:buildProperties.get("sourceCommit");
        return sha!=null && sha.matches("(?i)[a-f0-9]{40}")?sha:"UNVERIFIED";
    }
}
