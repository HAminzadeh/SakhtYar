package com.sakhtyar.integration.neshan;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.hamcrest.Matchers.containsString;

/** Mock HTTP is the only transport: these tests never contact the real Neshan domain. */
class Phase15NeshanFailureContractTest {
    private static final String DUMMY = "SYNTHETIC-NONSECRET";

    private record Fixture(NeshanGeoProvider adapter, MockRestServiceServer server) { }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.neshan.org");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var adapter = new NeshanGeoProvider(builder.build(),
                new NeshanProperties(true, "https://api.neshan.org", DUMMY, 10), new ObjectMapper());
        return new Fixture(adapter, server);
    }

    @Test void throttledResponseBecomesServiceUnavailableWithNoRetries() {
        Fixture f = fixture();
        f.server.expect(requestTo(containsString("/geocoding/v1")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> f.adapter.geocode("synthetic address"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        f.server.verify();
    }

    @Test void providerUnauthorizedDoesNotExposeKeyInError() {
        Fixture f = fixture();
        f.server.expect(requestTo(containsString("/v5/reverse")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> f.adapter.reverseGeocode(new BigDecimal("35.1"), new BigDecimal("51.1")));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        assertFalse(error.getMessage().contains(DUMMY));
        f.server.verify();
    }

    @Test void malformedJsonAndOversizedResponseDoNotEscape() {
        Fixture malformed = fixture();
        malformed.server.expect(requestTo(containsString("/geocoding/v1")))
                .andRespond(withSuccess("{bad", MediaType.APPLICATION_JSON));
        assertEquals(HttpStatus.BAD_GATEWAY,
                assertThrows(ResponseStatusException.class, () -> malformed.adapter.geocode("synthetic")).getStatusCode());
        malformed.server.verify();

        Fixture oversized = fixture();
        oversized.server.expect(requestTo(containsString("/geocoding/v1")))
                .andRespond(withSuccess("x".repeat(65_537), MediaType.APPLICATION_JSON));
        assertEquals(HttpStatus.BAD_GATEWAY,
                assertThrows(ResponseStatusException.class, () -> oversized.adapter.geocode("synthetic")).getStatusCode());
        oversized.server.verify();
    }

    @Test void validNestedResponsePreservesLatitudeLongitude() {
        Fixture f = fixture();
        f.server.expect(requestTo(containsString("/geocoding/v1")))
                .andRespond(withSuccess("{\"result\":{\"location\":{\"lat\":35.6892,\"lng\":51.3890},\"address\":\"Synthetic\"}}",
                        MediaType.APPLICATION_JSON));
        var result = f.adapter.geocode("synthetic");
        assertEquals(1, result.size());
        assertEquals(0, new BigDecimal("35.6892").compareTo(result.get(0).latitude()));
        assertEquals(0, new BigDecimal("51.3890").compareTo(result.get(0).longitude()));
        f.server.verify();
    }

    @Test void emptyReverseResponseIsRejected() {
        Fixture f = fixture();
        f.server.expect(requestTo(containsString("/v5/reverse")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ResponseStatusException.class,
                () -> f.adapter.reverseGeocode(BigDecimal.ONE, BigDecimal.ONE)).getStatusCode());
        f.server.verify();
    }
}
