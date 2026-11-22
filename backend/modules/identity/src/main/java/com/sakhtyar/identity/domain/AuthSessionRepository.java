package com.sakhtyar.identity.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository
        extends JpaRepository<AuthSessionEntity, UUID> {

    // Serialize concurrent refresh and revoke requests against the same token.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthSessionEntity> findByRefreshTokenHash(String refreshTokenHash);

    List<AuthSessionEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    // Token rotation and revocation must obtain the same row-level lock.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSessionEntity s where s.id = :sessionId")
    Optional<AuthSessionEntity> findByIdForUpdate(@Param("sessionId") UUID sessionId);

    // Used only for mutations; do not lock the read-only sessions UI query.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSessionEntity s where s.userId = :userId order by s.createdAt desc, s.id asc")
    List<AuthSessionEntity> findAllByUserIdForUpdate(@Param("userId") UUID userId);
}
