package com.sakhtyar.notification.application;

import com.sakhtyar.audit.application.AuditService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationScopeBoundaryTest {
    @Test void testerCannotDispatchEvenWithoutSessionScope() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        NotificationService svc=new NotificationService(jdbc,mock(AuditService.class),mock(ObjectMapper.class));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("tester",null,
                List.of(new SimpleGrantedAuthority("ROLE_TESTER"))));
        try {
            assertThrows(SecurityException.class,()->svc.enqueue(null));
            assertThrows(SecurityException.class,()->svc.get(UUID.randomUUID()));
            verifyNoInteractions(jdbc);
        } finally { SecurityContextHolder.clearContext(); }
    }
    @Test void testSessionScopeCannotReadRealOutbox() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(String.class))).thenReturn("TEST");
        NotificationService svc=new NotificationService(jdbc,mock(AuditService.class),mock(ObjectMapper.class));
        assertThrows(SecurityException.class,()->svc.pending(5));
    }
}
