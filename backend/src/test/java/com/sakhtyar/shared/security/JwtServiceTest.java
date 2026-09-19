package com.sakhtyar.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JwtServiceTest {

    @Test
    void createsAndVerifiesToken() {
        JwtService service = new JwtService(
                "a-very-long-development-secret-that-is-definitely-more-than-32-chars",
                60
        );

        String token = service.createToken("admin", "ADMIN");

        assertEquals("admin", service.verify(token).getSubject());
        assertEquals("ADMIN", service.verify(token).getClaim("role").asString());
    }

    @Test
    void rejectsShortSecret() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtService("too-short", 60)
        );
    }
}
