package com.sakhtyar.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Phase3 E2E-23 offline checks against the existing Phase06 TEST-only adapter. */
class Phase3NotificationOutboxIsolationTest {
    @Test void fakeOnlyScopedDedupAndTeardown() {
        Clock frozen=Clock.fixed(Instant.parse("2026-01-02T10:00:00Z"), ZoneOffset.UTC);
        var outbox=new Phase06FakeNotificationOutbox(frozen,Duration.ofSeconds(60));
        UUID w=UUID.randomUUID(), r=UUID.randomUUID(), other=UUID.randomUUID();
        var first=outbox.enqueue(w,r,"phase3_evt","FAKE_EMAIL");
        assertThat(first.status()).isEqualTo("PENDING");
        assertThat(outbox.enqueue(w,r,"phase3_evt","FAKE_EMAIL")).isEqualTo(first);
        assertThat(outbox.attempt(w,r,"phase3_evt",true).status()).isEqualTo("FAILED");
        assertThat(outbox.attempt(w,r,"phase3_evt",false).attempts()).isEqualTo(1);
        outbox.enqueue(w,other,"phase3_evt","FAKE_SMS");
        assertThat(outbox.teardown(w,r)).isEqualTo(1);
        assertThat(outbox.size()).isEqualTo(1);
        assertThatThrownBy(()->outbox.enqueue(w,r,"real_send","EMAIL")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->outbox.enqueue(w,r,"real_send","SMTP")).isInstanceOf(SecurityException.class);
    }
}
