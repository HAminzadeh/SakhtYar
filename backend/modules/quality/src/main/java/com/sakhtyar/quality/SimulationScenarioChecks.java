package com.sakhtyar.quality;

import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Real non-mutating prerequisites. Business workflows are separate and MUST NOT be inferred from these probes. */
@Service
public final class SimulationScenarioChecks {
    public record Outcome(String status, String detail, Map<String,Object> evidence) {}
    private final JdbcTemplate db;
    private final RequestMappingHandlerMapping mvc;
    private final SimulationCoreFixtureService fixture;
    public SimulationScenarioChecks(JdbcTemplate db,
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mvc,
            SimulationCoreFixtureService fixture) {
        this.db=db;this.mvc=mvc;this.fixture=fixture;
    }
    public Outcome check(SimulationScenarioPlans.Plan plan, int index) {
        try { return switch(index) {
            case 1 -> connectivity();
            case 2 -> tables(plan);
            case 3 -> routes(plan);
            case 4 -> gate(plan);
            default -> throw new IllegalArgumentException("Invalid prerequisite step: "+index);
        }; } catch (Exception ex) {
            // Don't expose SQL values or connection details in API; retain error class for diagnosis.
            return new Outcome("FAILED","Prerequisite failed: "+ex.getClass().getSimpleName(),
                Map.of("errorType",ex.getClass().getSimpleName()));
        }
    }
    private Outcome connectivity() {
        Integer one=db.queryForObject("select 1",Integer.class);
        boolean ok=Integer.valueOf(1).equals(one);
        return new Outcome(ok?"PASSED":"FAILED",ok?"PostgreSQL SELECT 1 succeeded":"PostgreSQL did not return 1",
            Map.of("assertion","SELECT_1","observed",one==null?0:one));
    }
    private Outcome tables(SimulationScenarioPlans.Plan plan) {
        List<String> missing=new ArrayList<>();
        for(String name:plan.requiredTables()) {
            if(!name.matches("[a-z][a-z0-9_]{1,79}")) throw new IllegalArgumentException("Invalid static table name");
            Boolean exists=db.queryForObject("select to_regclass(?) is not null",Boolean.class,"public."+name);
            if(!Boolean.TRUE.equals(exists)) missing.add(name);
        }
        boolean ok=missing.isEmpty();
        return new Outcome(ok?"PASSED":"FAILED",
            ok?"Required PostgreSQL tables verified ("+plan.requiredTables().size()+")":
              "Missing PostgreSQL tables: "+String.join(", ",missing),
            Map.of("required",plan.requiredTables(),"missing",missing,"assertion","CATALOG_TABLE_EXISTENCE"));
    }
    private Outcome routes(SimulationScenarioPlans.Plan plan) {
        List<String> missing=new ArrayList<>();
        for(String raw:plan.requiredRoutes()) {
            String[] parts=raw.split(" ",2);
            if(parts.length!=2 || mvc.getHandlerMethods().keySet().stream().noneMatch(info->
              info.getPatternValues().contains(parts[1]) &&
              (info.getMethodsCondition().getMethods().isEmpty() ||
               info.getMethodsCondition().getMethods().stream().anyMatch(x->x.name().equals(parts[0]))))) missing.add(raw);
        }
        boolean ok=missing.isEmpty();
        return new Outcome(ok?"PASSED":"FAILED",
            ok?"Spring MVC route registrations verified (HTTP NOT called)":"Missing route mappings: "+String.join(", ",missing),
            Map.of("required",plan.requiredRoutes(),"missing",missing,"assertion","ROUTE_REGISTRATION_ONLY"));
    }

    /**
     * Scenario-specific, READ-ONLY isolation and relational-integrity checks.
     * These checks examine live database catalog metadata. They do not create a
     * business object, invoke business HTTP APIs or grant an E2E PASS.
     */
    public Outcome domainIntegrity(SimulationScenarioPlans.Plan plan) {
        try {
            if ("E2E-05".equals(plan.id())) {
                Long failed=db.queryForObject(
                    "select count(*) from flyway_schema_history where not success",Long.class);
                Long applied=db.queryForObject(
                    "select count(*) from flyway_schema_history where success",Long.class);
                boolean healthy=Long.valueOf(0).equals(failed) && applied!=null && applied>0;
                return new Outcome(healthy?"PASSED":"FAILED",
                    healthy?"Live Flyway history contains no failed migration (restore drill NOT exercised)":
                            "Flyway history contains a failure or is empty",
                    Map.of("assertion","LIVE_FLYWAY_HISTORY","successfulMigrations",applied==null?0:applied,
                           "failedMigrations",failed==null?-1:failed,"restorationTested",false));
            }
            boolean restricted=Boolean.TRUE.equals(db.queryForObject(
                "select not rolsuper and not rolbypassrls from pg_roles where rolname=current_user",Boolean.class));
            List<String> noScope=new ArrayList<>(),noPolicy=new ArrayList<>(),noPk=new ArrayList<>();
            List<Map<String,Object>> observed=new ArrayList<>();
            for(String table:plan.requiredTables()) {
                // Identifiers originate only in the source-controlled, validated scenario catalog.
                if(!table.matches("[a-z][a-z0-9_]{1,79}"))
                    throw new IllegalArgumentException("Unexpected table identifier");
                List<Map<String,Object>> rows=db.queryForList("""
                    select c.relrowsecurity as rls_enabled,c.relforcerowsecurity as rls_forced,
                      exists(select 1 from pg_attribute a where a.attrelid=c.oid
                        and a.attname='data_scope' and a.attnum>0 and not a.attisdropped) as has_scope,
                      exists(select 1 from pg_attribute a where a.attrelid=c.oid
                        and a.attname='test_workspace_id' and a.attnum>0 and not a.attisdropped) as has_workspace,
                      exists(select 1 from pg_constraint k where k.conrelid=c.oid and k.contype='p') as has_pk
                    from pg_class c join pg_namespace n on n.oid=c.relnamespace
                    where n.nspname='public' and c.relname=? and c.relkind in ('r','p')
                    """,table);
                if(rows.size()!=1) {
                    noScope.add(table+" (missing)");noPolicy.add(table+" (missing)");
                    noPk.add(table+" (missing)");continue;
                }
                Map<String,Object> info=rows.get(0);
                boolean tagged=Boolean.TRUE.equals(info.get("has_scope")) && Boolean.TRUE.equals(info.get("has_workspace"));
                boolean rls=Boolean.TRUE.equals(info.get("rls_enabled")) && Boolean.TRUE.equals(info.get("rls_forced"));
                boolean pk=Boolean.TRUE.equals(info.get("has_pk"));
                if(!tagged)noScope.add(table);
                if(!rls)noPolicy.add(table);
                if(!pk)noPk.add(table);
                observed.add(Map.of("table",table,"hasDataScopeAndWorkspace",tagged,"rlsEnabledAndForced",rls,"primaryKey",pk));
            }
            // Explicit business relationship assertion, never inferred from an API route.
            Map<String,String> relationship=Map.ofEntries(
                Map.entry("E2E-02","construction_case_access"),
                Map.entry("E2E-07","knowledge_document"),
                Map.entry("E2E-08","knowledge_pipeline_checkpoint"),
                Map.entry("E2E-10","knowledge_ai_import_review_event"),
                Map.entry("E2E-11","knowledge_rule_review_event"),
                Map.entry("E2E-15","project_builder"),
                Map.entry("E2E-17","scenario_cost_snapshot"),
                Map.entry("E2E-19","financial_participation_allocation"));
            String related=relationship.get(plan.id());
            boolean foreignKeyOk=true;
            if(related!=null) {
                Long count=db.queryForObject("""
                    select count(*) from pg_constraint c join pg_class t on t.oid=c.conrelid
                    join pg_namespace n on n.oid=t.relnamespace
                    where n.nspname='public' and t.relname=? and c.contype='f'
                    """,Long.class,related);
                foreignKeyOk=count!=null && count>0;
            }
            boolean ready=restricted && noScope.isEmpty() && noPolicy.isEmpty() &&
                noPk.isEmpty() && foreignKeyOk;
            Map<String,Object> evidence=new LinkedHashMap<>();
            evidence.put("assertion","DOMAIN_SCHEMA_SCOPE_AUDIT_NOT_BUSINESS_E2E");
            evidence.put("scenarioId",plan.id());
            evidence.put("restrictedDatabaseRole",restricted);
            evidence.put("observations",observed);
            evidence.put("missingDataScopeOrWorkspace",noScope);
            evidence.put("missingForcedRls",noPolicy);
            evidence.put("missingPrimaryKey",noPk);
            evidence.put("foreignKeyTableChecked",related==null?"none":related);
            evidence.put("foreignKeyPresent",foreignKeyOk);
            evidence.put("businessWorkflowExecuted",false);
            String detail=ready?"Live DB contract and scope audit passed; business workflow NOT executed":
                "Domain scope audit BLOCKED: restrictedRole="+restricted+
                ", missingProvenance="+noScope.size()+", missingForcedRLS="+noPolicy.size()+
                ", missingPK="+noPk.size()+", FK="+foreignKeyOk;
            return new Outcome(ready?"PASSED":"BLOCKED",detail,evidence);
        } catch(Exception ex) {
            return new Outcome("FAILED","Domain audit failed: "+ex.getClass().getSimpleName(),
                Map.of("assertion","DOMAIN_SCHEMA_SCOPE_AUDIT_NOT_BUSINESS_E2E",
                       "errorType",ex.getClass().getSimpleName(),"businessWorkflowExecuted",false));
        }
    }

    private Outcome gate(SimulationScenarioPlans.Plan plan) {
        if("E2E-14".equals(plan.id())) {
            Map<String,Object> readiness=fixture.readiness();
            boolean ok=Boolean.TRUE.equals(readiness.get("coreFixturePermitted"));
            return new Outcome(ok?"PASSED":"BLOCKED",ok?
                "Limited 4-table CORE fixture is authorized under restricted PostgreSQL RLS role":
                "CORE fixture gated: "+readiness.getOrDefault("blockers",List.of("Runtime isolation not ready")),
                Map.of("coreFixturePermitted",ok,"runtimeDbUser",String.valueOf(readiness.get("runtimeDbUser")),
                       "fullApplicationIsolationVerified",false));
        }
        return new Outcome("BLOCKED",
            "No isolated full business executor or verified external-effect guard for "+plan.id()+
            ". Its safe database/route prerequisites were checked, but business operations remain disabled.",
            Map.of("businessWorkflowExecuted",false,"status","UNIMPLEMENTED_BUSINESS_WORKFLOW"));
    }
}
