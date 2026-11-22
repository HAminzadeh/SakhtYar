package com.sakhtyar.identity.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sakhtyar.identity.domain.AuthSessionEntity;
import com.sakhtyar.identity.domain.AuthSessionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class Phase05SessionRevocationTest {
    private static AuthSessionEntity session(UUID user) {
        return new AuthSessionEntity(UUID.randomUUID(), user, "a".repeat(64), "WEB", null,
                null, null, Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS));
    }

    @Test
    void revokingAnotherUsersSessionReturnsNotFoundWithoutMutation() {
        AuthSessionRepository repository = mock(AuthSessionRepository.class);
        AuthSessionEntity other = session(UUID.randomUUID());
        when(repository.findByIdForUpdate(other.getId())).thenReturn(Optional.of(other));
        AuthSessionService service = new AuthSessionService(repository, 30);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.revokeSession(UUID.randomUUID(), other.getId()));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(repository, never()).save(any(AuthSessionEntity.class));
    }

    @Test
    void revokeAllLocksSessionRowsAndMakesThemUnusable() {
        AuthSessionRepository repository = mock(AuthSessionRepository.class);
        UUID owner = UUID.randomUUID();
        AuthSessionEntity first = session(owner);
        AuthSessionEntity second = session(owner);
        when(repository.findAllByUserIdForUpdate(owner)).thenReturn(List.of(first, second));
        AuthSessionService service = new AuthSessionService(repository, 30);
        service.revokeAll(owner);
        assertFalse(first.isUsable());
        assertFalse(second.isUsable());
        verify(repository).findAllByUserIdForUpdate(owner);
        verify(repository).saveAll(anyList());
        verify(repository, never()).findAllByUserIdOrderByCreatedAtDesc(owner);
    }

    @Test
    void refreshReplayAfterRotationIsRejectedWhenOldHashIsGone() {
        AuthSessionRepository repository = mock(AuthSessionRepository.class);
        UUID owner = UUID.randomUUID();
        AuthSessionEntity entity = session(owner);
        // An actual PostgreSQL PESSIMISTIC_WRITE query serializes the two calls;
        // the second old-hash lookup then sees no matching row.
        when(repository.findByRefreshTokenHash(anyString()))
                .thenReturn(Optional.of(entity), Optional.empty());
        AuthSessionService service = new AuthSessionService(repository, 30);
        AuthSessionService.RotatedSession rotated = service.rotate("old-token");
        assertNotNull(rotated.refreshToken());
        assertNotEquals("old-token", rotated.refreshToken());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.rotate("old-token"));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verify(repository, times(1)).save(entity);
    }

    @Test
    void revokedRefreshSessionCannotBeRotated() {
        AuthSessionRepository repository = mock(AuthSessionRepository.class);
        AuthSessionEntity entity = session(UUID.randomUUID());
        entity.revoke(Instant.now());
        when(repository.findByRefreshTokenHash(anyString())).thenReturn(Optional.of(entity));
        AuthSessionService service = new AuthSessionService(repository, 30);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.rotate("revoked-token"));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verify(repository, never()).save(any(AuthSessionEntity.class));
    }
}
