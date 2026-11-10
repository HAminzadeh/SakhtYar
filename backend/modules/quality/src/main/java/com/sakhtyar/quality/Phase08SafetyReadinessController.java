package com.sakhtyar.quality;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Read-only, owner-authorized Phase 08 safety explanation, never a business run. */
@RestController
@RequestMapping("/api/v1/admin/simulations/e2e")
public final class Phase08SafetyReadinessController {
    private final JdbcTemplate jdbc;
    private final SimulationCoreFixtureService fixtures;
    private final SimulationWorkspaceAuthorizationService authorization;
    @Autowired(required=false) private BuildProperties buildProperties;
    @Value("${app.dev-seed.enabled:false}") private boolean devSeedEnabled;

    public Phase08SafetyReadinessController(JdbcTemplate jdbc,SimulationCoreFixtureService fixtures,
            SimulationWorkspaceAuthorizationService authorization) {
        this.jdbc=jdbc;this.fixtures=fixtures;this.authorization=authorization;
    }

    @GetMapping("/safety-readiness")
    public Map<String,Object> readiness(@RequestParam UUID workspaceId, Authentication actor) {
        if(actor==null || !actor.isAuthenticated() || actor.getAuthorities()==null ||
           actor.getAuthorities().stream().noneMatch(x->"USER_MANAGE".equals(x.getAuthority())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Simulation administrator required");
        // The client never provides the owner identity or the DB login assertion.
        authorization.requireActiveOwner(workspaceId,actor);
        Map<String,Object> databaseIdentity;
        try {
            databaseIdentity=jdbc.queryForMap("""
                select current_user::text as db_current_user,
                       session_user::text as db_session_user,
                       current_setting('row_security')::text as row_security
                """);
        } catch(RuntimeException ex) { databaseIdentity=Map.of(); }
        Map<String,Object> readiness;
        try { readiness=fixtures.readiness(); }
        catch(RuntimeException ex) { readiness=Map.of(); }
        String commit=buildProperties==null?"UNVERIFIED":buildProperties.get("sourceCommit");
        Phase08SafetyAdmission.Verdict decision=Phase08SafetyAdmission.assess(
            workspaceId,readiness,databaseIdentity,devSeedEnabled,commit);
        Map<String,Object> output=new LinkedHashMap<>(decision.report());
        output.put("mode","READ_ONLY_PHASE08_SECURITY_READINESS");
        output.put("checkedAt",Instant.now().toString());
        output.put("targetCount",7);
        output.put("targets",Phase08SecurityProofCatalog.report());
        output.put("fullBusinessScenariosProven",0);
        output.put("runtimeDbRoleProofDoesNotComeFromMainApplicationConnection",true);
        output.put("warning","TEST role provisioning, SQL, seed, restore, business HTTP and database cleanup were not invoked");
        return output;
    }
}
