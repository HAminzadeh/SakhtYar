package com.sakhtyar.identity.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sakhtyar.identity.domain.UserEntity;
import com.sakhtyar.identity.domain.UserRole;
import com.sakhtyar.identity.domain.UserStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    @Test
    void createsAndVerifiesAccessToken() {
        JwtService service = new JwtService(
                "a-very-long-development-secret-that-is-definitely-more-than-32-chars",
                60
        );

        UserEntity user = new UserEntity(
                UUID.randomUUID(),
                "admin",
                "hash",
                "Admin",
                null,
                null,
                UserRole.ADMIN,
                UserStatus.ACTIVE,
                Instant.now()
        );

        UUID sessionId = UUID.randomUUID();
        String token = service.createAccessToken(user, sessionId);

        assertEquals("admin", service.verify(token).getSubject());
        assertEquals(
                "ADMIN",
                service.verify(token).getClaim("role").asString()
        );
        assertEquals(
                sessionId.toString(),
                service.verify(token).getClaim("sid").asString()
        );
    }

    @Test
    void rejectsShortSecret() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtService("too-short", 60)
        );
    }
}
