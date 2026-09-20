package com.sakhtyar.integration.neshan;

import com.sakhtyar.geo.domain.GeoProvider;
import com.sakhtyar.geo.domain.GeoSearchResult;
import com.sakhtyar.geo.domain.ReverseGeocodeResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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

@Component
@ConditionalOnProperty(
        prefix = "app.integrations.neshan",
        name = "enabled",
        havingValue = "true"
)
public class NeshanGeoProvider implements GeoProvider {

    private final RestClient client;
    private final NeshanProperties properties;
    private final ObjectMapper objectMapper;

    public NeshanGeoProvider(
            RestClient neshanRestClient,
            NeshanProperties properties,
            ObjectMapper objectMapper
    ) {
        this.client = neshanRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<GeoSearchResult> geocode(String address) {
        requireApiKey();

        try {
            String payload = objectMapper.writeValueAsString(
                    Map.of("address", address)
            );

            String body = client.get()
                    .uri("/geocoding/v1?json={json}", payload)
                    .header("Api-Key", properties.serviceApiKey())
                    .retrieve()
                    .body(String.class);

            return parseGeocode(body, address);
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not connect to Neshan geocoding service.",
                    ex
            );
        } catch (JacksonException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not read Neshan geocoding response.",
                    ex
            );
        }
    }

    @Override
    public ReverseGeocodeResult reverseGeocode(
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        requireApiKey();

        try {
            String body = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v5/reverse")
                            .queryParam("lat", latitude)
                            .queryParam("lng", longitude)
                            .build())
                    .header("Api-Key", properties.serviceApiKey())
                    .retrieve()
                    .body(String.class);

            return parseReverse(body);
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not connect to Neshan reverse-geocoding service.",
                    ex
            );
        } catch (JacksonException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not read Neshan reverse-geocoding response.",
                    ex
            );
        }
    }

    private List<GeoSearchResult> parseGeocode(
            String body,
            String requestedAddress
    ) throws JacksonException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode candidate = unwrapCandidate(root);

        BigDecimal lat = firstDecimal(
                candidate,
                "location.latitude",
                "location.lat",
                "location.y",
                "latitude",
                "lat",
                "y"
        );
        BigDecimal lng = firstDecimal(
                candidate,
                "location.longitude",
                "location.lng",
                "location.x",
                "longitude",
                "lng",
                "x"
        );

        if (lat == null || lng == null) {
            // Some response envelopes keep the location on the root object.
            lat = firstDecimal(
                    root,
                    "location.latitude",
                    "location.lat",
                    "location.y",
                    "latitude",
                    "lat",
                    "y"
            );
            lng = firstDecimal(
                    root,
                    "location.longitude",
                    "location.lng",
                    "location.x",
                    "longitude",
                    "lng",
                    "x"
            );
        }

        if (lat == null || lng == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Neshan geocoding response did not contain coordinates."
            );
        }

        String returnedAddress = firstText(
                candidate,
                "formatted_address",
                "formattedAddress",
                "address"
        );
        if (returnedAddress == null) {
            returnedAddress = firstText(
                    root,
                    "formatted_address",
                    "formattedAddress",
                    "address"
            );
        }
        if (returnedAddress == null) {
            returnedAddress = requestedAddress;
        }

        String title = firstText(candidate, "title", "name");
        if (title == null) {
            title = returnedAddress;
        }

        return List.of(
                new GeoSearchResult(
                        title,
                        returnedAddress,
                        firstText(
                                candidate,
                                "neighbourhood",
                                "neighborhood"
                        ),
                        firstText(candidate, "city", "region"),
                        null,
                        lat,
                        lng
                )
        );
    }

    private ReverseGeocodeResult parseReverse(String body)
            throws JacksonException {
        JsonNode root = objectMapper.readTree(body);

        return new ReverseGeocodeResult(
                firstText(
                        root,
                        "formatted_address",
                        "formattedAddress",
                        "address"
                ),
                firstText(root, "state", "province"),
                firstText(root, "city"),
                firstText(
                        root,
                        "municipality_zone",
                        "municipalityZone",
                        "district"
                ),
                firstText(
                        root,
                        "neighbourhood",
                        "neighborhood"
                ),
                firstText(root, "route_name", "routeName"),
                firstText(root, "place")
        );
    }

    private JsonNode unwrapCandidate(JsonNode root) {
        JsonNode result = root.path("result");
        if (result.isObject()) {
            return result;
        }

        JsonNode data = root.path("data");
        if (data.isObject()) {
            return data;
        }

        JsonNode items = root.path("items");
        if (items.isArray() && !items.isEmpty()) {
            return items.get(0);
        }

        JsonNode results = root.path("results");
        if (results.isArray() && !results.isEmpty()) {
            return results.get(0);
        }

        return root;
    }

    private void requireApiKey() {
        if (properties.serviceApiKey() == null
                || properties.serviceApiKey().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "NESHAN_SERVICE_API_KEY is not configured."
            );
        }
    }

    private ResponseStatusException translate(
            RestClientResponseException ex
    ) {
        if (ex.getStatusCode().value() == 429) {
            return new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Neshan rate limit reached."
            );
        }

        if (ex.getStatusCode().value() == 401
                || ex.getStatusCode().value() == 403) {
            return new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Neshan API key was rejected or does not have access to the requested service."
            );
        }

        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Neshan service request failed."
        );
    }

    private String firstText(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode value = at(node, path);
            if (value != null
                    && !value.isNull()
                    && !value.asText("").isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private BigDecimal firstDecimal(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode value = at(node, path);
            if (value == null || value.isNull()) {
                continue;
            }

            try {
                if (value.isNumber()) {
                    return value.decimalValue();
                }

                String text = value.asText();
                if (text != null && !text.isBlank()) {
                    return new BigDecimal(text);
                }
            } catch (NumberFormatException ignored) {
                // Try the next known response shape.
            }
        }

        return null;
    }

    private JsonNode at(JsonNode node, String path) {
        JsonNode current = node;

        for (String part : path.split("\\.")) {
            if (current == null || current.isMissingNode()) {
                return null;
            }
            current = current.path(part);
        }

        return current;
    }
}
