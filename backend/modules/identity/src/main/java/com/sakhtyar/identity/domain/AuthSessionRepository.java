package com.sakhtyar.identity.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository
        extends JpaRepository<AuthSessionEntity, UUID> {

    Optional<AuthSessionEntity> findByRefreshTokenHash(String refreshTokenHash);

    List<AuthSessionEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
