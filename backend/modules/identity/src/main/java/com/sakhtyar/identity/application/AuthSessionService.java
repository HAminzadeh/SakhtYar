package com.sakhtyar.identity.application;

import com.sakhtyar.identity.domain.AuthSessionEntity;
import com.sakhtyar.identity.domain.AuthSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthSessionService {

    private final AuthSessionRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long refreshExpirationDays;

    public AuthSessionService(
            AuthSessionRepository repository,
            @Value("${app.security.refresh.expiration-days:30}")
            long refreshExpirationDays
    ) {
        this.repository = repository;
        this.refreshExpirationDays = refreshExpirationDays;
    }

    @Transactional
    public CreatedSession create(
            UUID userId,
            String clientType,
            String deviceName,
            String userAgent,
            String ipAddress
    ) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(refreshExpirationDays, ChronoUnit.DAYS);
        String plainToken = newToken();

        AuthSessionEntity entity = new AuthSessionEntity(
                UUID.randomUUID(),
                userId,
                hash(plainToken),
                clientType,
                clean(deviceName, 200),
                clean(userAgent, 1000),
                clean(ipAddress, 100),
                now,
                expiresAt
        );

        repository.save(entity);
        return new CreatedSession(entity, plainToken);
    }

    @Transactional
    public RotatedSession rotate(String plainRefreshToken) {
        AuthSessionEntity session = requireByToken(plainRefreshToken);

        if (!session.isUsable()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "نشست منقضی یا لغو شده است."
            );
        }

        String nextToken = newToken();
        session.rotate(hash(nextToken), Instant.now());
        repository.save(session);

        return new RotatedSession(session, nextToken);
    }

    @Transactional
    public void revokeByToken(String plainRefreshToken) {
        if (plainRefreshToken == null || plainRefreshToken.isBlank()) {
            return;
        }

        repository.findByRefreshTokenHash(hash(plainRefreshToken))
                .ifPresent(session -> {
                    session.revoke(Instant.now());
                    repository.save(session);
                });
    }

    @Transactional
    public void revokeSession(UUID userId, UUID sessionId) {
        AuthSessionEntity session = repository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "نشست پیدا نشد."
                ));

        if (!session.getUserId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "نشست پیدا نشد."
            );
        }

        session.revoke(Instant.now());
        repository.save(session);
    }

    @Transactional
    public void revokeAll(UUID userId) {
        List<AuthSessionEntity> sessions =
                repository.findAllByUserIdOrderByCreatedAtDesc(userId);

        Instant now = Instant.now();
        for (AuthSessionEntity session : sessions) {
            if (session.getRevokedAt() == null) {
                session.revoke(now);
            }
        }
        repository.saveAll(sessions);
    }

    @Transactional(readOnly = true)
    public List<AuthSessionEntity> list(UUID userId) {
        return repository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    private AuthSessionEntity requireByToken(String plainRefreshToken) {
        if (plainRefreshToken == null || plainRefreshToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Refresh token الزامی است."
            );
        }

        return repository.findByRefreshTokenHash(hash(plainRefreshToken))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "نشست معتبر نیست."
                ));
    }

    private String newToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable.", ex);
        }
    }

    private String clean(String value, int maxLength) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() <= maxLength
                ? trimmed
                : trimmed.substring(0, maxLength);
    }

    public record CreatedSession(
            AuthSessionEntity session,
            String refreshToken
    ) {
    }

    public record RotatedSession(
            AuthSessionEntity session,
            String refreshToken
    ) {
    }
}
