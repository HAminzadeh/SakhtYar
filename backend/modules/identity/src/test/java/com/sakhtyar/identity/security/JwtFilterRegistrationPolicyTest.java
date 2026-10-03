package com.sakhtyar.identity.security;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

class JwtFilterRegistrationPolicyTest {

    @Test
    void jwtFilterMustNotBeServletComponent() {
        assertFalse(
                JwtCookieAuthenticationFilter.class.isAnnotationPresent(Component.class),
                "JWT filter must be registered only in SecurityFilterChain."
        );
    }
}