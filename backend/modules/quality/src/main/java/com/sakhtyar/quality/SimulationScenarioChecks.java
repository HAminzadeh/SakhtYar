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
