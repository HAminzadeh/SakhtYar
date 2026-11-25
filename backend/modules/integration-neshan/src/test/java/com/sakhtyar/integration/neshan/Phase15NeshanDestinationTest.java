package com.sakhtyar.integration.neshan;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class Phase15NeshanDestinationTest {
    private NeshanProperties config(String url) {
        return new NeshanProperties(true, url, "SYNTHETIC-NONSECRET", 5,
                true, 10, 2, 5);
    }

    @Test void acceptsOnlyOfficialHttpsEndpoint() {
        assertDoesNotThrow(() -> NeshanDestinationPolicy.requireOfficialHttps(config("https://api.neshan.org")));
        assertDoesNotThrow(() -> NeshanDestinationPolicy.requireOfficialHttps(config("https://api.neshan.org:443/")));
        for (String url : new String[]{"http://api.neshan.org", "https://api.neshan.org.evil.example",
                "http://127.0.0.1:8080", "https://api.neshan.org@evil.example",
                "https://api.neshan.org:444", "https://api.neshan.org/api/", "https://api.neshan.org?key=fake"}) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> NeshanDestinationPolicy.requireOfficialHttps(config(url)));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
            assertFalse(error.getReason().contains(url));
        }
    }

    @Test void defaultSpringBoundRecordRequiresExplicitOutboundApproval() {
        var props = new NeshanProperties(true, "https://api.neshan.org", "SYNTHETIC-NONSECRET", 5,
                false, 0, 0, 0);
        var provider = new NeshanGeoProvider(null, props, null);
        assertFalse(provider.configured());
        // A null transport proves no HTTP path is reached while outbound is disabled.
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ResponseStatusException.class,
                        () -> provider.geocode("synthetic address")).getStatusCode());
    }
}
