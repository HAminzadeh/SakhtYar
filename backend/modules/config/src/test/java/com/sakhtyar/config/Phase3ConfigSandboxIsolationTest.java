package com.sakhtyar.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Phase3 E2E-24 checks existing Phase06 in-memory TEST sandbox, never real config. */
class Phase3ConfigSandboxIsolationTest {
    @Test void reviewedTestOnlyLifecycleAndSecretRejection() {
        var sandbox=new Phase06ConfigSandbox(UUID.randomUUID(),UUID.randomUUID());
        assertThatThrownBy(()->sandbox.draft("author",Map.of("test.api.secret","nope")))
            .isInstanceOf(SecurityException.class);
        var draft=sandbox.draft("author",Map.of("test.quality.threshold","42"));
        assertThat(draft.version()).isEqualTo(1);
        assertThat(draft.sha256()).matches("[a-f0-9]{64}");
        assertThatThrownBy(()->sandbox.review(draft.version(),"author"))
            .isInstanceOf(SecurityException.class);
        var reviewed=sandbox.review(draft.version(),"reviewer");
        assertThat(reviewed.status()).isEqualTo(Phase06ConfigSandbox.Status.REVIEWED);
        assertThatThrownBy(()->sandbox.publishInSandboxOnly(draft.version(),"author"))
            .isInstanceOf(SecurityException.class);
        var published=sandbox.publishInSandboxOnly(draft.version(),"publisher");
        assertThat(published.sha256()).isEqualTo(draft.sha256());
        assertThat(sandbox.rollbackInSandboxOnly(draft.version(),"reviewer").status())
            .isEqualTo(Phase06ConfigSandbox.Status.ROLLED_BACK);
    }
}
