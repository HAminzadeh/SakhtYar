package com.sakhtyar.quality.phase3;

import static org.assertj.core.api.Assertions.assertThat;
import com.sakhtyar.quality.*;
import com.sakhtyar.identity.domain.UserRole;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class Phase3RegistryFailClosedTest {
    @Test void fourOfflineExecutorsRegisteredButNeverProduceBusinessPass() {
        var registry=new ScenarioExecutorRegistry(List.of(new Phase3E2E21Executor(),new Phase3E2E22Executor(),
            new Phase3E2E23Executor(),new Phase3E2E24Executor()));
        var context=new E2EExecutionContext(UUID.randomUUID(),UUID.randomUUID(),"owner",UUID.randomUUID(),
            "tester",UserRole.TESTER,Set.of("ROLE_TESTER","QUALITY_VIEW","QUALITY_RUN_SAFE"),
            "UNVERIFIED",new AtomicBoolean(false),"trace");
        for(int n=21;n<=24;n++) {
            String id="E2E-"+n;
            assertThat(registry.supports(id)).isTrue();
            assertThat(registry.executionType(id)).isEqualTo("OFFLINE_CONTRACT_ONLY");
            var result=registry.execute(id,context);
            assertThat(result.state()).isEqualTo("BLOCKED");
            assertThat(result.evidence()).containsEntry("businessWorkflowExecuted",false);
            assertThat(result.evidence()).containsEntry("offlineContractPassed",true);
        }
    }
}
