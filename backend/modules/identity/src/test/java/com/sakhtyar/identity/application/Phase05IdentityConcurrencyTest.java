package com.sakhtyar.identity.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.auth.api.UserAdminDtos.UpdateUserRequest;
import com.sakhtyar.identity.domain.AuthSessionRepository;
import com.sakhtyar.identity.domain.UserEntity;
import com.sakhtyar.identity.domain.UserRepository;
import com.sakhtyar.identity.domain.UserRole;
import com.sakhtyar.identity.domain.UserStatus;
import com.sakhtyar.identity.mapper.UserMapper;
import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class Phase05IdentityConcurrencyTest {
    private static UserEntity account(UserRole role) {
        return new UserEntity(UUID.randomUUID(), "reviewed-user", "stored-hash", "Reviewed User",
                null, null, role, UserStatus.ACTIVE, Instant.now());
    }

    @Test
    void loginCounterUsesSerializedUserRowAndLocksAfterFiveFailures() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        when(passwords.encode(anyString())).thenReturn("dummy-hash");
        when(passwords.matches(anyString(), eq("stored-hash"))).thenReturn(false);
        UserEntity user = account(UserRole.READ_ONLY);
        when(users.findByUsernameIgnoreCaseForUpdate("reviewed-user")).thenReturn(Optional.of(user));
        AuthService auth = new AuthService(users, passwords, new PasswordPolicy(), mock(AuditService.class));
        for (int attempt = 1; attempt <= 5; attempt++) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> auth.authenticate("reviewed-user", "wrong-password"));
            assertEquals(attempt == 5 ? HttpStatus.LOCKED : HttpStatus.UNAUTHORIZED,
                    error.getStatusCode());
        }
        assertEquals(5, user.getFailedLoginAttempts());
        assertTrue(user.isTemporarilyLocked());
        verify(users, times(5)).findByUsernameIgnoreCaseForUpdate("reviewed-user");
        verify(users, times(5)).save(user);
        verify(users, never()).findByUsernameIgnoreCase(anyString());
    }

    @Test
    void roleChangeRevokesActiveSessionsEvenWhenStatusRemainsActive() {
        UserRepository users = mock(UserRepository.class);
        AuthSessionService sessions = mock(AuthSessionService.class);
        UserEntity user = account(UserRole.ANALYST);
        when(users.lockUsersByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE)).thenReturn(List.of());
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        UserAdminService admin = new UserAdminService(users, mock(UserMapper.class),
                mock(PasswordEncoder.class), new PasswordPolicy(), sessions, mock(AuditService.class));
        admin.update(user.getId(), new UpdateUserRequest("Reviewed User", null, null,
                UserRole.TESTER, UserStatus.ACTIVE), "another-admin");
        assertEquals(UserRole.TESTER, user.getRole());
        verify(sessions).revokeAll(user.getId());
        verify(users).lockUsersByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE);
    }

    @Test
    void unchangedRoleAndActiveStatusDoNotInvalidateSessions() {
        UserRepository users = mock(UserRepository.class);
        AuthSessionService sessions = mock(AuthSessionService.class);
        UserEntity user = account(UserRole.READ_ONLY);
        when(users.lockUsersByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE)).thenReturn(List.of());
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        UserAdminService admin = new UserAdminService(users, mock(UserMapper.class),
                mock(PasswordEncoder.class), new PasswordPolicy(), sessions, mock(AuditService.class));
        admin.update(user.getId(), new UpdateUserRequest("Updated Name", null, null,
                UserRole.READ_ONLY, UserStatus.ACTIVE), "another-admin");
        verifyNoInteractions(sessions);
    }

    @Test
    void cannotDemoteOnlyActiveAdminAfterAcquiringAdministratorLocks() {
        UserRepository users = mock(UserRepository.class);
        UserEntity adminEntity = account(UserRole.ADMIN);
        when(users.lockUsersByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of(adminEntity));
        when(users.findById(adminEntity.getId())).thenReturn(Optional.of(adminEntity));
        UserAdminService admin = new UserAdminService(users, mock(UserMapper.class),
                mock(PasswordEncoder.class), new PasswordPolicy(), mock(AuthSessionService.class),
                mock(AuditService.class));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> admin.update(adminEntity.getId(), new UpdateUserRequest("Administrator", null,
                        null, UserRole.READ_ONLY, UserStatus.ACTIVE), "another-admin"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(users).lockUsersByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE);
        verify(users, never()).save(any(UserEntity.class));
        assertEquals(UserRole.ADMIN, adminEntity.getRole());
    }

    @Test
    void repositoryQueriesRequirePessimisticWriteLocks() throws Exception {
        assertPessimistic(UserRepository.class.getMethod("findByUsernameIgnoreCaseForUpdate", String.class));
        assertPessimistic(UserRepository.class.getMethod("lockUsersByRoleAndStatus", UserRole.class, UserStatus.class));
        assertPessimistic(AuthSessionRepository.class.getMethod("findByRefreshTokenHash", String.class));
        assertPessimistic(AuthSessionRepository.class.getMethod("findByIdForUpdate", UUID.class));
        assertPessimistic(AuthSessionRepository.class.getMethod("findAllByUserIdForUpdate", UUID.class));
    }

    private static void assertPessimistic(Method method) {
        Lock lock = method.getAnnotation(Lock.class);
        assertNotNull(lock, method.getName());
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }
}
