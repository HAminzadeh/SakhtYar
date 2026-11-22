package com.sakhtyar.identity.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.identity.domain.UserEntity;
import com.sakhtyar.identity.domain.UserRepository;
import com.sakhtyar.identity.domain.UserRole;
import com.sakhtyar.identity.domain.UserStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

class Phase05AdminBootstrapSafetyTest {
    @Test
    void existingNonAdminAccountIsNotPromotedOrResetByDefault() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        AuditService audit = mock(AuditService.class);
        UserEntity existing = new UserEntity(UUID.randomUUID(), "bootstrap-review", "existing-hash",
                "System Administrator", null, null, UserRole.READ_ONLY, UserStatus.PENDING, Instant.now());
        when(users.findByUsernameIgnoreCase("bootstrap-review")).thenReturn(Optional.of(existing));
        AdminBootstrap bootstrap = new AdminBootstrap(users, passwords, audit,
                "bootstrap-review", "unused-secret-not-logged", "System Administrator", false);
        assertDoesNotThrow(() -> bootstrap.run(mock(ApplicationArguments.class)));
        assertEquals(UserRole.READ_ONLY, existing.getRole());
        assertEquals(UserStatus.PENDING, existing.getStatus());
        verify(users, never()).save(any(UserEntity.class));
        verify(passwords, never()).encode(anyString());
        verifyNoInteractions(audit);
    }
}
