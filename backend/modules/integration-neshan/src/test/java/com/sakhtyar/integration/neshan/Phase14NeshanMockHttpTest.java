package com.sakhtyar.integration.neshan;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.hamcrest.Matchers.containsString;

/** All provider tests use Spring's in-memory HTTP server; NO live network/API key. */
class Phase14NeshanMockHttpTest {
    private static final String OFFLINE_KEY = "SYNTHETIC-OFFLINE-KEY";

    @Test void geocodeParsesWgs84CoordinatesWithoutRealHttp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.neshan.org");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("/geocoding/v1")))
                .andExpect(header("Api-Key", OFFLINE_KEY))
                .andRespond(withSuccess("{\"location\":{\"latitude\":35.6892,\"longitude\":51.3890},\"address\":\"Tehran\"}",
                        MediaType.APPLICATION_JSON));
        var adapter = new NeshanGeoProvider(builder.build(),
                new NeshanProperties(true, "https://api.neshan.org", OFFLINE_KEY, 10), new ObjectMapper());
        var results = adapter.geocode("Tehran");
        assertEquals(1, results.size());
        assertEquals(new BigDecimal("35.6892"), results.get(0).latitude());
        assertEquals(new BigDecimal("51.3890"), results.get(0).longitude());
        server.verify();
    }

    @Test void invalidProviderCoordinatesNeverEscapeAdapter() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.neshan.org");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("/geocoding/v1")))
                .andRespond(withSuccess("{\"location\":{\"latitude\":125,\"longitude\":51}}", MediaType.APPLICATION_JSON));
        var adapter = new NeshanGeoProvider(builder.build(),
                new NeshanProperties(true, "https://api.neshan.org", OFFLINE_KEY, 10), new ObjectMapper());
        assertThrows(ResponseStatusException.class, () -> adapter.geocode("Tehran"));
        server.verify();
    }

    @Test void reverseGeocodeUsesLatLngOrderAndMockTransport() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.neshan.org");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("/v5/reverse?lat=35.6892&lng=51.3890")))
                .andRespond(withSuccess("{\"formatted_address\":\"Tehran\",\"city\":\"Tehran\"}", MediaType.APPLICATION_JSON));
        var adapter = new NeshanGeoProvider(builder.build(),
                new NeshanProperties(true, "https://api.neshan.org", OFFLINE_KEY, 10), new ObjectMapper());
        var result = adapter.reverseGeocode(new BigDecimal("35.6892"), new BigDecimal("51.3890"));
        assertEquals("Tehran", result.city());
        server.verify();
    }
}
