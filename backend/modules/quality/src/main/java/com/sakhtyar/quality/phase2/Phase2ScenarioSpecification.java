package com.sakhtyar.quality.phase2;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;

/** Source-controlled, side-effect-free acceptance requirements. No business PASS authorization. */
public final class Phase2ScenarioSpecification {
    private Phase2ScenarioSpecification() {}
    public record Definition(String id, String domain, List<String> dependencies, String fixture,
                             String negative, String expected, List<String> requiredEvidence) {
        public Definition {
            dependencies=List.copyOf(dependencies);
            requiredEvidence=List.copyOf(requiredEvidence);
        }
    }
    private static final Map<String,Definition> ALL=build();
    private static Map<String,Definition> build() {
        Map<String,Definition> m=new LinkedHashMap<>();
        add(m,new Definition("E2E-07","KNOWLEDGE_ZIP_IMPORT",List.of("E2E-03"),"synthetic versioned zip with manifest/checksum","malformed archive, duplicate, wrong role","import remains unpublished; no external upload",
            List.of("AUTHENTICATED_POSITIVE_HTTP","AUTHENTICATED_NEGATIVE_HTTP","RESTRICTED_TEST_DB","BROWSER_ASSERTION","SHA256_ARTIFACTS","RUN_SCOPED_CLEANUP","NO_REAL_EGRESS")));
        add(m,new Definition("E2E-08","KNOWLEDGE_PIPELINE",List.of("E2E-07"),"deterministic synthetic pipeline checkpoint","wrong workspace, duplicate request, cancelled job","checkpoint replay preserves digest",
            List.of("AUTHENTICATED_POSITIVE_HTTP","AUTHENTICATED_NEGATIVE_HTTP","RESTRICTED_TEST_DB","BROWSER_ASSERTION","SHA256_ARTIFACTS","RUN_SCOPED_CLEANUP","NO_REAL_EGRESS")));
        add(m,new Definition("E2E-09","OCR_PROVENANCE",List.of("E2E-08"),"synthetic paginated document with source citation","missing page, unsupported file, fabricated citation","citation page must match text lineage",
            List.of("AUTHENTICATED_POSITIVE_HTTP","AUTHENTICATED_NEGATIVE_HTTP","RESTRICTED_TEST_DB","BROWSER_ASSERTION","SHA256_ARTIFACTS","RUN_SCOPED_CLEANUP","NO_REAL_EGRESS")));
        add(m,new Definition("E2E-10","HUMAN_REVIEW",List.of("E2E-09"),"synthetic revision and review event history","unauthorized reviewer, replayed revision","version monotonic and audit retained",
            List.of("AUTHENTICATED_POSITIVE_HTTP","AUTHENTICATED_NEGATIVE_HTTP","RESTRICTED_TEST_DB","BROWSER_ASSERTION","SHA256_ARTIFACTS","RUN_SCOPED_CLEANUP","NO_REAL_EGRESS")));
        add(m,new Definition("E2E-11","GOVERNANCE",List.of("E2E-10"),"witnessed staged draft and review","missing witness, unauthorized publish","rollback target retained; no REAL publication",
            List.of("AUTHENTICATED_POSITIVE_HTTP","AUTHENTICATED_NEGATIVE_HTTP","RESTRICTED_TEST_DB","BROWSER_ASSERTION","SHA256_ARTIFACTS","RUN_SCOPED_CLEANUP","NO_REAL_EGRESS")));
        add(m,new Definition("E2E-12","SCOPED_RETRIEVAL",List.of("E2E-11"),"versioned synthetic index","cross-workspace retrieval, deleted source","only permitted cited results returned",
            List.of("AUTHENTICATED_POSITIVE_HTTP","AUTHENTICATED_NEGATIVE_HTTP","RESTRICTED_TEST_DB","BROWSER_ASSERTION","SHA256_ARTIFACTS","RUN_SCOPED_CLEANUP","NO_REAL_EGRESS")));
        add(m,new Definition("E2E-13","CRAWLER",List.of("E2E-07"),"stubbed HTML and redirect graph","private address, redirect to private IP, duplicate URL","no real outbound network, bounded iteration",
            List.of("AUTHENTICATED_POSITIVE_HTTP","AUTHENTICATED_NEGATIVE_HTTP","RESTRICTED_TEST_DB","BROWSER_ASSERTION","SHA256_ARTIFACTS","RUN_SCOPED_CLEANUP","NO_REAL_EGRESS")));
        add(m,new Definition("E2E-15","BUILDER_OFFERS",List.of("E2E-14"),"synthetic builder, case and offer","foreign owner, repeated invite, live notification","owner-only result, notification fake",
            List.of("AUTHENTICATED_POSITIVE_HTTP","AUTHENTICATED_NEGATIVE_HTTP","RESTRICTED_TEST_DB","BROWSER_ASSERTION","SHA256_ARTIFACTS","RUN_SCOPED_CLEANUP","NO_REAL_EGRESS")));
        add(m,new Definition("E2E-16","MATERIAL_ESTIMATE",List.of("E2E-14"),"unit-normalized synthetic price observations","wrong unit, invalid price, anomaly","median/outlier/waste golden independent",
            List.of("AUTHENTICATED_POSITIVE_HTTP","AUTHENTICATED_NEGATIVE_HTTP","RESTRICTED_TEST_DB","BROWSER_ASSERTION","SHA256_ARTIFACTS","RUN_SCOPED_CLEANUP","NO_REAL_EGRESS")));
        add(m,new Definition("E2E-17","COST_SCENARIO",List.of("E2E-16"),"synthetic case and cost snapshot","cross-case request, invalid quantity, duplicate","stable replay digest and scoped read",
            List.of("AUTHENTICATED_POSITIVE_HTTP","AUTHENTICATED_NEGATIVE_HTTP","RESTRICTED_TEST_DB","BROWSER_ASSERTION","SHA256_ARTIFACTS","RUN_SCOPED_CLEANUP","NO_REAL_EGRESS")));
        add(m,new Definition("E2E-18","URBAN_FEASIBILITY",List.of("E2E-04", "E2E-17"),"versioned synthetic rule and parcel area","zero or negative parcel, unauthorized rule","FAR arithmetic and source lineage",
            List.of("AUTHENTICATED_POSITIVE_HTTP","AUTHENTICATED_NEGATIVE_HTTP","RESTRICTED_TEST_DB","BROWSER_ASSERTION","SHA256_ARTIFACTS","RUN_SCOPED_CLEANUP","NO_REAL_EGRESS")));
        add(m,new Definition("E2E-19","FINANCE_ALLOCATION",List.of("E2E-15", "E2E-18"),"decimal revenue, cost and 60/40 split","zero denominator, excess shares, foreign case","total preserved after half-even rounding",
            List.of("AUTHENTICATED_POSITIVE_HTTP","AUTHENTICATED_NEGATIVE_HTTP","RESTRICTED_TEST_DB","BROWSER_ASSERTION","SHA256_ARTIFACTS","RUN_SCOPED_CLEANUP","NO_REAL_EGRESS")));
        add(m,new Definition("E2E-20","SENSITIVITY",List.of("E2E-19"),"frozen input revenue/cost shock","mutated baseline, nondeterministic replay","immutable parent snapshot and derivative digest",
            List.of("AUTHENTICATED_POSITIVE_HTTP","AUTHENTICATED_NEGATIVE_HTTP","RESTRICTED_TEST_DB","BROWSER_ASSERTION","SHA256_ARTIFACTS","RUN_SCOPED_CLEANUP","NO_REAL_EGRESS")));
        return Map.copyOf(m);
    }
    private static void add(Map<String,Definition> m,Definition d) {
        if(m.putIfAbsent(d.id(),d)!=null) throw new IllegalStateException("Duplicate Phase2 E2E ID");
    }
    public static Map<String,Definition> all() { return ALL; }
    public static Definition get(String id) {
        Definition d=ALL.get(id);
        if(d==null) throw new IllegalArgumentException("Unknown Phase2 scenario: "+id);
        return d;
    }
    public static boolean acceptanceEvidenceComplete(Map<String,Boolean> proof) {
        if(proof==null) return false;
        return Set.of("AUTHENTICATED_POSITIVE_HTTP","AUTHENTICATED_NEGATIVE_HTTP",
            "RESTRICTED_TEST_DB","BROWSER_ASSERTION","SHA256_ARTIFACTS",
            "RUN_SCOPED_CLEANUP","NO_REAL_EGRESS").stream().allMatch(k->Boolean.TRUE.equals(proof.get(k)));
    }
}
