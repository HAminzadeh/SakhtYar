package com.sakhtyar.integration.neshan;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class Phase14NeshanEgressPolicyTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void noCredentialsMeansNotReadyAndNoNetwork() {
        var provider = new NeshanGeoProvider(null,
                new NeshanProperties(true, "https://api.neshan.org", "", 10), null);
        assertFalse(provider.configured());
        var failure = assertThrows(ResponseStatusException.class, () -> provider.geocode("Tehran"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.getStatusCode());
    }

    @Test void invalidCoordinateRefusedBeforeNetwork() {
        var provider = new NeshanGeoProvider(null,
                new NeshanProperties(true, "https://api.neshan.org", "TEST-NOT-A-REAL-KEY", 10), null);
        assertTrue(provider.configured());
        var failure = assertThrows(ResponseStatusException.class,
                () -> provider.reverseGeocode(new BigDecimal("95"), BigDecimal.ONE));
        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
    }

    @Test void testerCannotInitiateNetworkEvenWhenKeyConfigured() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("tester", null,
                        List.of(new SimpleGrantedAuthority("ROLE_TESTER"))));
        var provider = new NeshanGeoProvider(null,
                new NeshanProperties(true, "https://api.neshan.org", "TEST-NOT-A-REAL-KEY", 10), null);
        assertThrows(SecurityException.class, () -> provider.geocode("Tehran"));
        assertThrows(SecurityException.class, () -> provider.reverseGeocode(BigDecimal.ONE, BigDecimal.ONE));
    }
}
