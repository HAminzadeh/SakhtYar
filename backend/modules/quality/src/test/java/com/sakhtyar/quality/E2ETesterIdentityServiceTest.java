package com.sakhtyar.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.sakhtyar.identity.domain.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

class E2ETesterIdentityServiceTest {
    UserRepository users = mock(UserRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    UserDetailsService details = mock(UserDetailsService.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    E2ETesterIdentityService service = new E2ETesterIdentityService(users, encoder, details, jdbc);

    private UserEntity user(UserRole role, UserStatus status) {
        return new UserEntity(UUID.randomUUID(), "tester", "stored_hash", "Tester", null, null, role, status, Instant.now());
    }

    @Test void existingActiveTesterIsReusedWithoutPasswordChange() {
        UserEntity existing=user(UserRole.TESTER,UserStatus.ACTIVE);
        when(users.findByUsernameIgnoreCase("tester")).thenReturn(Optional.of(existing));
        assertThat(service.ensure().action()).isEqualTo("REUSED");
        verify(users,never()).saveAndFlush(any());
        assertThat(existing.getPasswordHash()).isEqualTo("stored_hash");
    }

    @Test void missingTesterIsCreatedWithHashedPassword() {
        when(users.findByUsernameIgnoreCase("tester")).thenReturn(Optional.empty());
        when(encoder.encode(any())).thenReturn("new_encoded_hash");
        when(users.saveAndFlush(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.ensure().action()).isEqualTo("CREATED");
        verify(encoder).encode(any());
        verify(users).saveAndFlush(any(UserEntity.class));
    }

    @Test void adminNameConflictIsNeverDowngradedOrModified() {
        when(users.findByUsernameIgnoreCase("tester")).thenReturn(Optional.of(user(UserRole.ADMIN,UserStatus.ACTIVE)));
        assertThatThrownBy(() -> service.ensure()).isInstanceOf(IllegalStateException.class);
        verify(users,never()).saveAndFlush(any());
    }

    @Test void disabledTesterRemainsDisabled() {
        when(users.findByUsernameIgnoreCase("tester")).thenReturn(Optional.of(user(UserRole.TESTER,UserStatus.SUSPENDED)));
        assertThatThrownBy(() -> service.ensure()).isInstanceOf(IllegalStateException.class);
        verify(users,never()).saveAndFlush(any());
    }
}
