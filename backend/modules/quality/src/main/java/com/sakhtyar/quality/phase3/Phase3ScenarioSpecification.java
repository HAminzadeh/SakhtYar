package com.sakhtyar.quality.phase3;

import java.util.List;
import java.util.Map;

/** Explicit acceptance dependencies. Offline oracles are NOT live business E2E. */
public final class Phase3ScenarioSpecification {
    private Phase3ScenarioSpecification() {}
    public record Definition(String id,String domain,List<String> dependencies,String fixture,String negative,
                             String requiredLiveEvidence) {}
    private static final Map<String,Definition> ITEMS=Map.of(
        "E2E-21", new Definition("E2E-21","AI_AGENTS",List.of("E2E-03","E2E-12"),
            "TEST workflow, restricted fake AI and tool ACL","prompt injection, forbidden tool, provider failure",
            "Authenticated workflow HTTP, restricted DB, denied external egress, redacted audit, verified artifacts"),
        "E2E-22", new Definition("E2E-22","BROWSER_RTL",List.of("E2E-01","E2E-02","E2E-14"),
            "TEST owner/builder/admin and synthetic case","cross-workspace 403/404, blocked external requests",
            "Real local Playwright session, RTL/mobile, BFF HTTP, TEST DB principal, signed artifacts"),
        "E2E-23", new Definition("E2E-23","NOTIFICATION_MONITORING",List.of("E2E-01"),
            "TEST-only synthetic threshold event and fake outbox","duplicate, cooldown, denied real send",
            "Authenticated API and DB outbox proof, zero real sends, bounded retry, signed artifacts"),
        "E2E-24", new Definition("E2E-24","CONFIG_SANDBOX",List.of("E2E-03"),
            "Versioned isolated TEST config draft","secret keys, same-user review, real publish",
            "Authenticated draft/review/rollback API, restricted DB, immutable audit, signed artifacts")
    );
    public static Definition get(String id) {
        Definition d=ITEMS.get(id);if(d==null)throw new IllegalArgumentException("Missing specification "+id);return d;
    }
    public static Map<String,Definition> all() {return ITEMS;}
}
