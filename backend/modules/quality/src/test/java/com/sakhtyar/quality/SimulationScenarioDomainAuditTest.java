package com.sakhtyar.quality;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

class SimulationScenarioDomainAuditTest {
    static class FakeJdbc extends JdbcTemplate {
        boolean restricted=true;
        boolean scoped=true;
        // JdbcTemplate has TWO overloads. Production invokes the no-arguments
        // variant for role and Flyway checks; both must use the same fake response.
        @Override public <T> T queryForObject(String sql,Class<T> type) {
            return queryForObject(sql,type,new Object[0]);
        }
        @Override public <T> T queryForObject(String sql,Class<T> type,Object... args) {
            Object value;
            if(sql.contains("not rolsuper and not rolbypassrls")) value=restricted;
            else if(sql.contains("where not success")) value=0L;
            else if(sql.contains("where success")) value=62L;
            else if(sql.contains("pg_constraint")) value=1L;
            else throw new IllegalStateException("Unexpected query in test: "+sql);
            return type.cast(value);
        }
        @Override public List<Map<String,Object>> queryForList(String sql,Object... args) {
            if(!sql.contains("pg_class")) throw new IllegalStateException("Unexpected query in test: "+sql);
            return List.of(Map.of("rls_enabled",scoped,"rls_forced",scoped,
                "has_scope",scoped,"has_workspace",scoped,"has_pk",true));
        }
    }
    @Test void everyScenarioHasExecutableDomainContractYetNeverClaimsBusinessE2e() {
        FakeJdbc db=new FakeJdbc();
        var checker=new SimulationScenarioChecks(db,null,null);
        for(var plan:SimulationScenarioPlans.all()) {
            var result=checker.domainIntegrity(plan);
            assertEquals("PASSED",result.status(),result.detail());
            if(!"E2E-05".equals(plan.id())) {
                assertEquals(false,result.evidence().get("businessWorkflowExecuted"));
                assertEquals("DOMAIN_SCHEMA_SCOPE_AUDIT_NOT_BUSINESS_E2E",result.evidence().get("assertion"));
            }
        }
        assertEquals(24,SimulationScenarioPlans.all().size());
    }
    @Test void superuserRoleMustNeverSatisfyDomainIsolationGate() {
        FakeJdbc db=new FakeJdbc();db.restricted=false;
        var checker=new SimulationScenarioChecks(db,null,null);
        for(var plan:SimulationScenarioPlans.all()) {
            if("E2E-05".equals(plan.id())) continue; // Flyway integrity does not use an isolation role.
            var result=checker.domainIntegrity(plan);
            assertEquals("BLOCKED",result.status(),result.detail());
            assertEquals(false,result.evidence().get("restrictedDatabaseRole"));
        }
    }
    @Test void missingRlsAndProvenanceMustBlockAWorkflow() {
        FakeJdbc db=new FakeJdbc();db.scoped=false;
        var checker=new SimulationScenarioChecks(db,null,null);
        var result=checker.domainIntegrity(SimulationScenarioPlans.get("E2E-19"));
        assertEquals("BLOCKED",result.status(),result.detail());
        assertFalse(((List<?>)result.evidence().get("missingForcedRls")).isEmpty());
        assertFalse(((List<?>)result.evidence().get("missingDataScopeOrWorkspace")).isEmpty());
    }
}
