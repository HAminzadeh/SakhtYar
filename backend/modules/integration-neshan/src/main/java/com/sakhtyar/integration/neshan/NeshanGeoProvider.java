package com.sakhtyar.integration.neshan;

import com.sakhtyar.geo.application.GeoInputPolicy;
import com.sakhtyar.geo.domain.GeoProvider;
import com.sakhtyar.geo.domain.GeoSearchResult;
import com.sakhtyar.geo.domain.ReverseGeocodeResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Neshan boundary. TESTER and missing-credential paths never initiate external HTTP. */
@Component
@ConditionalOnProperty(prefix = "app.integrations.neshan", name = "enabled", havingValue = "true")
public class NeshanGeoProvider implements GeoProvider {
    private final RestClient client;
    private final NeshanProperties properties;
    private final ObjectMapper objectMapper;
    private final NeshanTrafficGuard traffic;
    private static final int MAX_RESPONSE_CHARS = 65_536;

    /** Legacy constructor retained for in-memory mocked transport tests. */
    public NeshanGeoProvider(RestClient neshanRestClient, NeshanProperties properties, ObjectMapper objectMapper) {
        this(neshanRestClient, properties, objectMapper, new NeshanTrafficGuard(properties));
    }

    @Autowired
    public NeshanGeoProvider(RestClient neshanRestClient, NeshanProperties properties,
            ObjectMapper objectMapper, NeshanTrafficGuard traffic) {
        this.client = neshanRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.traffic = traffic;
    }

    @Override
    public boolean configured() {
        if (properties == null || !properties.enabled() || !properties.outboundEnabled() ||
                properties.serviceApiKey() == null || properties.serviceApiKey().isBlank()) return false;
        try {
            NeshanDestinationPolicy.requireOfficialHttps(properties);
            return true;
        } catch (ResponseStatusException ex) {
            return false;
        }
    }

    @Override
    public List<GeoSearchResult> geocode(String address) {
        denyTesterOutbound();
        requireApiKey();
        final String cleaned;
        try { cleaned = GeoInputPolicy.address(address); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage()); }
        traffic.beforeRequest();
        try {
            String payload = objectMapper.writeValueAsString(Map.of("address", cleaned));
            String body = client.get()
                    .uri("/geocoding/v1?json={json}", payload)
                    .header("Api-Key", properties.serviceApiKey())
                    .retrieve()
                    .body(String.class);
            List<GeoSearchResult> result = parseGeocode(body, cleaned);
            traffic.recordSuccess();
            return result;
        } catch (RestClientResponseException ex) {
            traffic.recordFailure();
            throw translate(ex);
        } catch (RestClientException ex) {
            traffic.recordFailure();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Neshan geocoding request failed.");
        } catch (JacksonException ex) {
            traffic.recordFailure();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Neshan response was invalid.");
        } catch (ResponseStatusException ex) {
            traffic.recordFailure();
            throw ex;
        }
    }

    @Override
    public ReverseGeocodeResult reverseGeocode(BigDecimal latitude, BigDecimal longitude) {
        denyTesterOutbound();
        requireApiKey();
        try { GeoInputPolicy.point(latitude, longitude); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage()); }
        traffic.beforeRequest();
        try {
            String body = client.get()
                    .uri(uriBuilder -> uriBuilder.path("/v5/reverse")
                            .queryParam("lat", latitude).queryParam("lng", longitude).build())
                    .header("Api-Key", properties.serviceApiKey())
                    .retrieve()
                    .body(String.class);
            ReverseGeocodeResult result = parseReverse(body);
            traffic.recordSuccess();
            return result;
        } catch (RestClientResponseException ex) {
            traffic.recordFailure();
            throw translate(ex);
        } catch (RestClientException ex) {
            traffic.recordFailure();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Neshan reverse-geocoding request failed.");
        } catch (JacksonException ex) {
            traffic.recordFailure();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Neshan response was invalid.");
        } catch (ResponseStatusException ex) {
            traffic.recordFailure();
            throw ex;
        }
    }

    private List<GeoSearchResult> parseGeocode(String body, String requestedAddress) throws JacksonException {
        JsonNode root = responseRoot(body);
        JsonNode candidate = unwrapCandidate(root);
        BigDecimal lat = firstDecimal(candidate,
                "location.latitude", "location.lat", "location.y", "latitude", "lat", "y");
        BigDecimal lng = firstDecimal(candidate,
                "location.longitude", "location.lng", "location.x", "longitude", "lng", "x");
        if (lat == null || lng == null) {
            lat = firstDecimal(root,
                    "location.latitude", "location.lat", "location.y", "latitude", "lat", "y");
            lng = firstDecimal(root,
                    "location.longitude", "location.lng", "location.x", "longitude", "lng", "x");
        }
        if (lat == null || lng == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Neshan geocoding response did not contain coordinates.");
        }
        // Do not return untrusted provider coordinates outside WGS84 to the map UI.
        try { GeoInputPolicy.point(lat, lng); }
        catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Neshan geocoding response contained invalid coordinates.");
        }
        String returnedAddress = firstText(candidate, "formatted_address", "formattedAddress", "address");
        if (returnedAddress == null)
            returnedAddress = firstText(root, "formatted_address", "formattedAddress", "address");
        if (returnedAddress == null) returnedAddress = requestedAddress;
        String title = firstText(candidate, "title", "name");
        if (title == null) title = returnedAddress;
        return List.of(new GeoSearchResult(title, returnedAddress,
                firstText(candidate, "neighbourhood", "neighborhood"),
                firstText(candidate, "city", "region"), null, lat, lng));
    }

    private ReverseGeocodeResult parseReverse(String body) throws JacksonException {
        JsonNode root = responseRoot(body);
        ReverseGeocodeResult result = new ReverseGeocodeResult(
                firstText(root, "formatted_address", "formattedAddress", "address"),
                firstText(root, "state", "province"),
                firstText(root, "city"),
                firstText(root, "municipality_zone", "municipalityZone", "district"),
                firstText(root, "neighbourhood", "neighborhood"),
                firstText(root, "route_name", "routeName"),
                firstText(root, "place")
        );
        if (result.formattedAddress() == null && result.city() == null && result.district() == null)
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Neshan reverse result is empty.");
        return result;
    }

    private JsonNode responseRoot(String body) throws JacksonException {
        if (body == null || body.isBlank() || body.length() > MAX_RESPONSE_CHARS)
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Neshan response size is invalid.");
        JsonNode root = objectMapper.readTree(body);
        if (root == null || !root.isObject())
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Neshan response object is invalid.");
        return root;
    }

    private JsonNode unwrapCandidate(JsonNode root) {
        JsonNode result = root.path("result");
        if (result.isObject()) return result;
        JsonNode data = root.path("data");
        if (data.isObject()) return data;
        JsonNode items = root.path("items");
        if (items.isArray() && !items.isEmpty()) return items.get(0);
        JsonNode results = root.path("results");
        if (results.isArray() && !results.isEmpty()) return results.get(0);
        return root;
    }

    private static void denyTesterOutbound() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_TESTER".equals(a.getAuthority()))) {
            throw new SecurityException("TESTER map-provider egress blocked; use offline mock");
        }
    }

    private void requireApiKey() {
        if (!configured())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Neshan outbound service is disabled or credentials are unavailable.");
        NeshanDestinationPolicy.requireOfficialHttps(properties);
    }

    private ResponseStatusException translate(RestClientResponseException ex) {
        if (ex.getStatusCode().value() == 429)
            return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Neshan rate limit reached.");
        if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403)
            return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Neshan API key was rejected or does not have access to the requested service.");
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Neshan service request failed.");
    }

    private String firstText(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode value = at(node, path);
            if (value != null && !value.isNull() && !value.asText("").isBlank()) {
                String text = value.asText().strip();
                if (text.length() > 4000 || text.codePoints().anyMatch(Character::isISOControl))
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid Neshan response text.");
                return text;
            }
        }
        return null;
    }

    private BigDecimal firstDecimal(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode value = at(node, path);
            if (value == null || value.isNull()) continue;
            try {
                if (value.isNumber()) return value.decimalValue();
                String text = value.asText();
                if (text != null && !text.isBlank()) return new BigDecimal(text);
            } catch (NumberFormatException ignored) {
                // Try the next known provider response field.
            }
        }
        return null;
    }

    private JsonNode at(JsonNode node, String path) {
        JsonNode current = node;
        for (String part : path.split("\\.")) {
            if (current == null || current.isMissingNode()) return null;
            current = current.path(part);
        }
        return current;
    }
}
