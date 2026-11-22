package com.sakhtyar.identity.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.auth.api.AuthDtos.RegisterRequest;
import com.sakhtyar.auth.api.UserAdminDtos.CreateUserRequest;
import com.sakhtyar.identity.domain.UserEntity;
import com.sakhtyar.identity.domain.UserRepository;
import com.sakhtyar.identity.domain.UserRole;
import com.sakhtyar.identity.domain.UserStatus;
import com.sakhtyar.identity.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class Phase05RegistrationRaceTest {
    @Test
    void twoRegistrationsWithSameUsernameReturnSanitizedConflictAfterUniqueCollision() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        AuditService audit = mock(AuditService.class);
        when(passwords.encode(anyString())).thenReturn("encoded-hash");
        when(users.saveAndFlush(any(UserEntity.class)))
                .thenThrow(new DataIntegrityViolationException("private DB constraint and user details"));
        AuthService auth = new AuthService(users, passwords, new PasswordPolicy(), audit);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> auth.register(new RegisterRequest("member_01", "Member Test", null, null,
                        "StrongPassword_123")));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertFalse(error.getReason().contains("private DB"));
        verifyNoInteractions(audit);
        verify(users).saveAndFlush(any(UserEntity.class));
    }

    @Test
    void twoAdminTesterCreationsWithSameUsernameReturnConflictBeforeAudit() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        AuditService audit = mock(AuditService.class);
        when(passwords.encode(anyString())).thenReturn("encoded-hash");
        when(users.saveAndFlush(any(UserEntity.class)))
                .thenThrow(new DataIntegrityViolationException("private DB constraint and user details"));
        UserAdminService admin = new UserAdminService(users, mock(UserMapper.class), passwords,
                new PasswordPolicy(), mock(AuthSessionService.class), audit);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> admin.create(new CreateUserRequest("tester_01", "Safe Tester", null, null,
                        "StrongPassword_123", UserRole.TESTER, UserStatus.ACTIVE)));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertFalse(error.getReason().contains("private DB"));
        verifyNoInteractions(audit);
        verify(users).saveAndFlush(any(UserEntity.class));
    }
}
