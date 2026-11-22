package com.sakhtyar.audit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.sakhtyar.audit.application.AuditPayloadSanitizer;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.audit.domain.AuditEventEntity;
import com.sakhtyar.audit.domain.AuditEventRepository;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

class Phase06AuditServiceTest {
    @AfterEach void cleanup() { MDC.clear(); }

    @Test void auditRecordMethodsJoinBusinessTransaction() throws Exception {
        for (Method m : List.of(
                AuditService.class.getMethod("record", String.class, UUID.class, String.class, Map.class),
                AuditService.class.getMethod("recordAs", String.class, UUID.class, String.class, String.class, Map.class))) {
            Transactional tx = m.getAnnotation(Transactional.class);
            assertNotNull(tx);
            assertEquals(Propagation.REQUIRED, tx.propagation());
        }
    }

    @Test void redactsSecretsPiiFreeTextAndUntrustedWorkspaceIdentity() {
        MDC.put("traceId", "AABBCCDDEEFF00112233445566778899");
        Map<String, Object> safe = AuditPayloadSanitizer.sanitize(Map.of(
                "password", "secret-value", "jwt", "Bearer abc", "email", "private@example.test",
                "workspaceId", "forged", "status", "ACTIVE", "attempts", 3,
                "note", "private address", "token", "access.jwt"));
        assertEquals("ACTIVE", safe.get("status"));
        assertEquals(3, safe.get("attempts"));
        assertEquals("aabbccddeeff00112233445566778899", safe.get("traceId"));
        assertFalse(safe.toString().contains("secret-value"));
        assertFalse(safe.toString().contains("private@example.test"));
        assertFalse(safe.toString().contains("forged"));
        assertFalse(safe.containsKey("workspaceId"));
        assertFalse(safe.containsKey("password"));
    }

    @Test void savesOnlySanitizedPayloadAndUniqueEventIds() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        AuditService service = new AuditService(repository, new ObjectMapper());
        UUID aggregate = UUID.randomUUID();
        service.recordAs("USER", aggregate, "USER_UPDATED", "admin",
                Map.of("password", "PASSWORD_NOT_ALLOWED", "status", "ACTIVE"));
        service.recordAs("USER", aggregate, "USER_UPDATED", "admin",
                Map.of("status", "ACTIVE"));
        ArgumentCaptor<AuditEventEntity> captor = ArgumentCaptor.forClass(AuditEventEntity.class);
        verify(repository, times(2)).save(captor.capture());
        var events = captor.getAllValues();
        assertEquals(2, events.size());
        assertNotEquals(events.get(0).getId(), events.get(1).getId());
        assertFalse(events.get(0).getPayloadText().contains("PASSWORD_NOT_ALLOWED"));
        assertTrue(events.get(0).getPayloadText().contains("ACTIVE"));
    }

    @Test void rejectsUnsafeAuditActionAndUnboundedPayload() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        AuditService service = new AuditService(repository, new ObjectMapper());
        assertThrows(IllegalArgumentException.class, () -> service.recordAs(
                "USER", UUID.randomUUID(), "Bearer sensitive jwt", "system", Map.of()));
        Map<String, Object> many = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 33; i++) many.put("secret" + i, "unsafe");
        assertThrows(IllegalArgumentException.class, () -> service.recordAs(
                "USER", UUID.randomUUID(), "SAFE_ACTION", "system", many));
        verifyNoInteractions(repository);
    }
}
