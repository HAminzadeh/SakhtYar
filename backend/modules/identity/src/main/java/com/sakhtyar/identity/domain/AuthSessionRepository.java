package com.sakhtyar.identity.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository
        extends JpaRepository<AuthSessionEntity, UUID> {

    // Serialize concurrent refresh and revoke requests against the same token.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthSessionEntity> findByRefreshTokenHash(String refreshTokenHash);

    List<AuthSessionEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
