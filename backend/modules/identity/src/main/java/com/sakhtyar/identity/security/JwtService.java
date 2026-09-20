package com.sakhtyar.identity.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.sakhtyar.identity.domain.UserEntity;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final Algorithm algorithm;
    private final long accessExpirationMinutes;

    public JwtService(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.access-expiration-minutes:30}")
            long accessExpirationMinutes
    ) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException(
                    "APP_JWT_SECRET must be at least 32 characters."
            );
        }
        this.algorithm = Algorithm.HMAC256(secret);
        this.accessExpirationMinutes = accessExpirationMinutes;
    }

    public String createAccessToken(UserEntity user, UUID sessionId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(
                accessExpirationMinutes,
                ChronoUnit.MINUTES
        );

        return JWT.create()
                .withIssuer("sakhtyar")
                .withSubject(user.getUsername())
                .withClaim("uid", user.getId().toString())
                .withClaim("role", user.getRole().name())
                .withClaim("sid", sessionId.toString())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiresAt))
                .sign(algorithm);
    }

    public DecodedJWT verify(String token) {
        return JWT.require(algorithm)
                .withIssuer("sakhtyar")
                .build()
                .verify(token);
    }

    public long accessExpirationSeconds() {
        return accessExpirationMinutes * 60;
    }
}
