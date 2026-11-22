package com.sakhtyar.quality;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Isolated read-only UI preflight session. It owns NO DB fixtures/roles/outbound resources.
 * The only cleanup needed is discarding the in-memory session; no delete SQL is ever issued.
 */
@RestController
@Profile("development & !production")
@RequestMapping("/api/v1/admin/simulations/e2e/preflight-sessions")
public final class Phase09PreflightSessionController {
    public record OpenRequest(UUID workspaceId) {}
    private record Session(UUID id, UUID workspace, String actor, Instant createdAt, Map<String,Object> report) {}
    private final Map<UUID,Session> live=new ConcurrentHashMap<>();
    private final SimulationWorkspaceAuthorizationService authorization;
    private final Phase08SafetyReadinessController safety;
    private final Phase09RuntimePolicy policy;
    public Phase09PreflightSessionController(SimulationWorkspaceAuthorizationService authorization,
            Phase08SafetyReadinessController safety,Phase09RuntimePolicy policy) {
        this.authorization=authorization;this.safety=safety;this.policy=policy;
    }
    @PostMapping public Map<String,Object> open(@RequestBody OpenRequest request,Authentication actor) {
        policy.requireDevelopment();
        if(request==null || request.workspaceId()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Workspace required");
        authorization.requireActiveOwner(request.workspaceId(),actor);
        synchronized(live) {
            prune();
            if(live.size()>=32) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Preflight session capacity reached");
            // Reuse the owner-protected, read-only evidence service. No business writes.
            Map<String,Object> proof=safety.readiness(request.workspaceId(),actor);
            UUID id=UUID.randomUUID();Instant now=Instant.now();
            Session s=new Session(id,request.workspaceId(),actor.getName(),now,Map.copyOf(proof));
            live.put(id,s);
            return describe(s);
        }
    }
    @GetMapping("/{id}") public Map<String,Object> show(@PathVariable UUID id,Authentication actor) {
        Session s=owned(id,actor);return describe(s);
    }
    @DeleteMapping("/{id}") public Map<String,Object> close(@PathVariable UUID id,Authentication actor) {
        Session s=owned(id,actor);live.remove(s.id(),s);
        return Map.of("id",s.id().toString(),"state","CLOSED","databaseResourcesCreated",false,
            "cleanup","IN_MEMORY_PRE_FLIGHT_ONLY_NO_SQL_OR_OUTBOUND","productionDataTouched",false);
    }
    private Session owned(UUID id,Authentication actor) {
        prune(); Session s=live.get(id);
        if(s==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Session not found");
        authorization.requireActiveOwner(s.workspace(),actor);
        if(!s.actor().equalsIgnoreCase(actor.getName())) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Session not found");
        return s;
    }
    private void prune() {
        Instant cutoff=Instant.now().minus(Duration.ofMinutes(15));
        live.values().removeIf(s->s.createdAt().isBefore(cutoff));
    }
    private static Map<String,Object> describe(Session s) {
        Map<String,Object> r=new LinkedHashMap<>();
        r.put("id",s.id().toString());r.put("workspaceId",s.workspace().toString());
        r.put("createdAt",s.createdAt().toString());r.put("expiresAt",s.createdAt().plus(Duration.ofMinutes(15)).toString());
        r.put("mode","READ_ONLY_PREFLIGHT_SESSION_NOT_BUSINESS_E2E");
        r.put("state","INSPECTION_ONLY");r.put("databaseResourcesCreated",false);
        r.put("businessWorkflowsExecuted",false);r.put("evidence",s.report());
        return r;
    }
}
