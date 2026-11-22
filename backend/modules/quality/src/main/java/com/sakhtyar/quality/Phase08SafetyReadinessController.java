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
@org.springframework.context.annotation.Profile("!production")
@RestController
@RequestMapping("/api/v1/admin/simulations/e2e")
public final class Phase08SafetyReadinessController {
    private final JdbcTemplate jdbc;
    private final SimulationCoreFixtureService fixtures;
    private final SimulationWorkspaceAuthorizationService authorization;
    @Autowired(required=false) private BuildProperties buildProperties;
    @Autowired(required=false) private Phase09RestrictedLoginInspector phase09RestrictedProbe;
    @Autowired(required=false) private Phase09RuntimePolicy phase09RuntimePolicy;
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
        Map<String,Object> appDatabaseIdentity;
        try {
            appDatabaseIdentity=jdbc.queryForMap("""
                select current_user::text as db_current_user,
                       session_user::text as db_session_user,
                       current_setting('row_security')::text as row_security
                """);
        } catch(RuntimeException ex) { appDatabaseIdentity=Map.of(); }
        Map<String,Object> readiness;
        try { readiness=fixtures.readiness(); }
        catch(RuntimeException ex) { readiness=Map.of(); }
        // The application's JDBC identity is diagnostic ONLY. Never use it to authorize E2E.
        Map<String,Object> restricted=phase09RestrictedProbe==null?Map.of("state","BLOCKED","blocker","PROBE_COMPONENT_MISSING"):
            phase09RestrictedProbe.inspect(workspaceId);
        Map<String,Object> physicalIdentity=Map.of();
        if(Boolean.TRUE.equals(restricted.get("physicalRestrictedLogin")) &&
           Boolean.TRUE.equals(restricted.get("restrictedRolePrivilegesVerified"))) {
            physicalIdentity=Map.of("db_current_user",restricted.get("db_current_user"),
                "db_session_user",restricted.get("db_session_user"),"row_security",restricted.get("row_security"));
            Map<String,Object> copy=new LinkedHashMap<>(readiness);
            copy.put("runtimeDbRoleNonBypass",true);
            readiness=copy;
        }
        String commit=buildProperties==null?"UNVERIFIED":buildProperties.get("sourceCommit");
        Phase08SafetyAdmission.Verdict decision=Phase08SafetyAdmission.assess(
            workspaceId,readiness,physicalIdentity,devSeedEnabled,commit);
        Map<String,Object> output=new LinkedHashMap<>(decision.report());
        output.put("mode","READ_ONLY_PHASE08_SECURITY_READINESS");
        output.put("profile",phase09RuntimePolicy==null?"UNKNOWN":phase09RuntimePolicy.profile());
        output.put("appJdbcIdentityNotAcceptedAsTestProof",true);
        output.put("restrictedLoginProbe",restricted);
        output.put("blockerDetails",Phase09SafetyBlockerCatalog.report(decision.blockers()));
        output.put("checkedAt",Instant.now().toString());
        output.put("targetCount",7);
        output.put("targets",Phase08SecurityProofCatalog.report());
        output.put("fullBusinessScenariosProven",0);
        output.put("runtimeDbRoleProofDoesNotComeFromMainApplicationConnection",true);
        output.put("warning","TEST role provisioning, SQL, seed, restore, business HTTP and database cleanup were not invoked");
        return output;
    }
}
