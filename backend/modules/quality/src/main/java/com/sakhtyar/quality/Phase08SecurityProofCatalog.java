package com.sakhtyar.quality;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Seven source-defined Phase 08 security scenario acceptance contracts. No executor is faked. */
public final class Phase08SecurityProofCatalog {
    private Phase08SecurityProofCatalog() {}
    public record Requirement(String scenarioId, String phase, String capability,
            List<String> requiredRealProof, boolean requiresGuardedDbWrite) {
        public Requirement { requiredRealProof=List.copyOf(requiredRealProof); }
        public Map<String,Object> report() {
            Map<String,Object> m=new LinkedHashMap<>();
            m.put("scenario_id",scenarioId);
            m.put("phase",phase);
            m.put("capability",capability);
            m.put("requiredRealProof",requiredRealProof);
            m.put("requiresGuardedDbWrite",requiresGuardedDbWrite);
            m.put("businessWorkflowExecuted",false);
            m.put("businessAcceptance","BLOCKED_UNTIL_REAL_PROOF");
            return m;
        }
    }
    private static final List<Requirement> TARGETS=List.of(
        new Requirement("E2E-01","S02","AUTH_SESSION_LIFECYCLE",List.of(
            "real authenticated TEST login/logout", "expiry and revocation", "negative post-logout HTTP"),true),
        new Requirement("E2E-02","S02","CASE_MEMBERSHIP_IDOR",List.of(
            "TEST principals with separate cases", "grant and revoke", "cross-case 403/404 over real HTTP"),true),
        new Requirement("E2E-03","S02","KNOWLEDGE_RBAC",List.of(
            "admin versus non-admin authenticated requests", "explicit 403", "real audit event"),false),
        new Requirement("E2E-04","S02","URBAN_RULE_REVIEW",List.of(
            "TEST draft/review/reject", "source and version evidence", "no REAL publication"),true),
        new Requirement("E2E-05","S02","FLYWAY_BACKUP_RECOVERY",List.of(
            "read-only Flyway integrity", "operator-verified backup/checksum", "restore drill: BLOCKED by no-clone policy"),false),
        new Requirement("E2E-06","S02","COOKIE_CSRF_NEGATIVE_HTTP",List.of(
            "real session with valid cookie and CSRF", "missing/invalid/replayed CSRF cases", "no secret capture"),true),
        new Requirement("E2E-14","S04","CASE_PROPERTY_OWNER",List.of(
            "restricted four-table TEST fixture", "300 sqm 60/40 owners", "real HTTP, non-member rejection and REAL invisibility"),true)
    );
    public static List<Requirement> all() { return TARGETS; }
    public static List<Map<String,Object>> report() { return TARGETS.stream().map(Requirement::report).toList(); }
}
