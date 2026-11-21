package com.sakhtyar.quality.phase2;

import com.sakhtyar.quality.ScenarioExecutor;
import com.sakhtyar.quality.ScenarioExecutorRegistry;
import com.sakhtyar.quality.E2EExecutionContext;
import com.sakhtyar.identity.domain.UserRole;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase2RegistryOfflineContractTest {
    private List<ScenarioExecutor> executors() {
        return List.of(new Phase2E2E07Executor(),new Phase2E2E08Executor(),new Phase2E2E09Executor(),
          new Phase2E2E10Executor(),new Phase2E2E11Executor(),new Phase2E2E12Executor(),
          new Phase2E2E13Executor(),new Phase2E2E15Executor(),new Phase2E2E16Executor(),
          new Phase2E2E17Executor(),new Phase2E2E18Executor(),new Phase2E2E19Executor(),
          new Phase2E2E20Executor());
    }
    private static E2EExecutionContext context() {
        return new E2EExecutionContext(UUID.randomUUID(),UUID.randomUUID(),"admin",
            UUID.randomUUID(),"tester",UserRole.TESTER,
            Set.of("ROLE_TESTER","QUALITY_VIEW","QUALITY_RUN_SAFE"),"a".repeat(40),
            new AtomicBoolean(false),UUID.randomUUID().toString());
    }
    @Test void allThirteenRegisterAndEveryOfflineOutcomeIsBlocked() {
        var registry=new ScenarioExecutorRegistry(executors());
        assertEquals(13,registry.registeredIds().size());
        var ctx=context();
        for(String id:registry.registeredIds()) {
            assertEquals("OFFLINE_CONTRACT_ONLY",registry.executionType(id));
            var outcome=registry.execute(id,ctx);
            assertEquals("BLOCKED",outcome.state(),id+" "+outcome.summary());
            assertEquals(false,outcome.evidence().get("businessWorkflowExecuted"));
            assertEquals(false,outcome.evidence().get("dbAssertion"));
            assertEquals(false,outcome.evidence().get("positiveHttpAssertion"));
            assertEquals(true,outcome.evidence().get("offlineContractPassed"));
            assertEquals(7,((java.util.List<?>)outcome.evidence().get("requiredAcceptanceEvidence")).size());
        }
    }
    @Test void nullOrCancelledContextCannotReachEffects() {
        var executor=new Phase2E2E07Executor();
        assertEquals("BLOCKED",executor.execute(null).state());
        var ctx=context(); ctx.cancellation().set(true);
        assertThrows(IllegalStateException.class,()->executor.execute(ctx));
    }
}
