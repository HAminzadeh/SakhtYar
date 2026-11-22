package com.sakhtyar.audit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.sakhtyar.audit.api.AuditReadController;
import com.sakhtyar.audit.domain.AuditEventEntity;
import com.sakhtyar.audit.domain.AuditEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class Phase06AuditReadControllerTest {
    private static Authentication auth(String... roles) {
        return new UsernamePasswordAuthenticationToken("admin", "n/a",
                java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }

    @Test void testerAndAnonymousCannotReadAuditOrReachDatabase() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        AuditReadController controller = new AuditReadController(repository);
        for (Authentication principal : new Authentication[]{null, auth("ROLE_TESTER", "AUDIT_READ"),
                auth("QUALITY_VIEW")}) {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> controller.list(0, 25, null, null, principal));
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }
        verifyNoInteractions(repository);
    }

    @Test void boundedAdminReadReturnsOnlyMetadataAndSafeTrace() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        AuditEventEntity event = new AuditEventEntity(UUID.randomUUID(), "USER", UUID.randomUUID(),
                "USER_UPDATED", "admin", "{\"traceId\":\"aabbccddeeff00112233445566778899\",\"password\":\"hidden\"}", Instant.now());
        when(repository.findAllByOrderByCreatedAtDesc(any())).thenReturn(new PageImpl<>(List.of(event)));
        var page = new AuditReadController(repository).list(0, 25, null, null, auth("AUDIT_READ"));
        assertEquals(1, page.getTotalElements());
        assertEquals("aabbccddeeff00112233445566778899", page.getContent().get(0).traceId());
        assertFalse(page.getContent().get(0).toString().contains("hidden"));
        assertEquals(7, page.getContent().get(0).getClass().getRecordComponents().length);
    }

    @Test void rejectsInvalidPaginationOrPartialFilterWithoutReadingDatabase() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        AuditReadController controller = new AuditReadController(repository);
        assertThrows(ResponseStatusException.class, () -> controller.list(0, 500, null, null, auth("AUDIT_READ")));
        assertThrows(ResponseStatusException.class, () -> controller.list(-1, 1, null, null, auth("AUDIT_READ")));
        assertThrows(ResponseStatusException.class, () -> controller.list(0, 10, "USER", null, auth("AUDIT_READ")));
        verifyNoInteractions(repository);
    }
}
