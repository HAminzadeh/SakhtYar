package com.sakhtyar.identity.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class SecurityDiagnosticsFilterTest {

    @Test
    void diagnosticsFilterCanBeConstructed() {
        assertNotNull(new SecurityDiagnosticsFilter());
    }
}