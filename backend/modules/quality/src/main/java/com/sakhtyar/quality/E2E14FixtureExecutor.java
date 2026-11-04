package com.sakhtyar.quality;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** E2E-14 remains a LIMITED four-table fixture; NEVER a complete business E2E pass. */
@Component
public final class E2E14FixtureExecutor implements ScenarioExecutor {
    private final SimulationCoreFixtureService fixture;
    private final JdbcTemplate jdbc;

    public E2E14FixtureExecutor(SimulationCoreFixtureService fixture, JdbcTemplate jdbc) {
        this.fixture = fixture;
        this.jdbc = jdbc;
    }

    @Override public String scenarioId() { return "E2E-14"; }
    @Override public String executionType() { return "LIMITED_CORE_FIXTURE"; }

    private void step(UUID id, int step, String state, String detail) {
        jdbc.update("""
            update simulation_e2e_step set state=?,detail=?,started_at=coalesce(started_at,now()),
              finished_at=case when ?='RUNNING' then null else now() end
            where run_id=? and scenario_id='E2E-14' and step_no=?
            """, state, detail, state, id, step + 4);
    }

    @Override public Outcome execute(E2EExecutionContext context) {
        UUID run = context.runId();
        context.requireNotCancelled();
        step(run, 1, "RUNNING", "Checking restricted PostgreSQL fixture role");
        Map<String, Object> readiness = fixture.readiness();
        if (!Boolean.TRUE.equals(readiness.get("coreFixturePermitted"))) {
            step(run, 1, "BLOCKED", "T05 restricted TEST fixture gate not ready");
            return new Outcome("BLOCKED", "Restricted TEST fixture gate not ready",
                Map.of("businessWorkflowExecuted", false, "fixtureStatus", "BLOCKED"));
        }
        step(run, 1, "PASSED", "Restricted TEST fixture gate passed");
        try {
            step(run, 2, "RUNNING", "Writing four-table TEST fixture");
            context.requireNotCancelled();
            String key = "e2e14_" + run.toString().replace("-", "");
            Map<String, Object> inserted = fixture.create(context.workspaceId(), key, context.testerUsername());
            step(run, 2, "PASSED", "Committed TEST fixture");
            step(run, 3, "RUNNING", "Reloading TEST fixture and verifying two owners");
            Object caseId = inserted.get("case_id");
            List<Map<String, Object>> observed = fixture.list(context.workspaceId());
            boolean ok = observed.stream().anyMatch(row -> Objects.equals(caseId, row.get("case_id")) &&
                row.get("owner_count") instanceof Number n && n.longValue() == 2L);
            if (!ok) throw new IllegalStateException("Four-table fixture assertions failed");
            step(run, 3, "PASSED", "Fixture and two owners verified");
            step(run, 4, "PASSED", "REAL-mode invisibility asserted by guarded fixture service");
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("businessWorkflowExecuted", false);
            evidence.put("fixtureStatus", "PASSED");
            evidence.put("fixtureType", "FOUR_TABLE_TEST_ONLY");
            evidence.put("caseId", String.valueOf(caseId));
            evidence.put("httpAssertion", false);
            evidence.put("browserAssertion", false);
            return new Outcome("BLOCKED", "Limited fixture PASSED; complete HTTP/browser business lifecycle NOT executed", evidence);
        } catch (Exception ex) {
            jdbc.update("""
                update simulation_e2e_step set state='BLOCKED',detail='Fixture interrupted/failed',finished_at=now()
                where run_id=? and scenario_id='E2E-14' and state in ('QUEUED','RUNNING')
                """, run);
            return new Outcome("FAILED", "Limited fixture failed: " + ex.getClass().getSimpleName(),
                Map.of("businessWorkflowExecuted", false, "fixtureStatus", "FAILED",
                       "errorType", ex.getClass().getSimpleName()));
        }
    }
}
