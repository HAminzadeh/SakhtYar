package com.sakhtyar.integration.neshan;

import com.sakhtyar.geo.domain.GeoProvider;
import com.sakhtyar.geo.domain.GeoSearchResult;
import com.sakhtyar.geo.domain.ReverseGeocodeResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
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
    public List<GeoSearchResult> search(
            String term,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        requireApiKey();

        try {
            String payload = objectMapper.writeValueAsString(
                    Map.of(
                            "term", term,
                            "center", Map.of(
                                    "latitude", latitude,
                                    "longitude", longitude
                            )
                    )
            );

            String body = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v3/search")
                            .queryParam("q", payload)
                            .build())
                    .header("Api-Key", properties.serviceApiKey())
                    .retrieve()
                    .body(String.class);

            return parseSearch(body);
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        } catch (JacksonException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not read Neshan search response."
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
        } catch (JacksonException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not read Neshan reverse-geocoding response."
            );
        }
    }

    private List<GeoSearchResult> parseSearch(String body)
            throws JacksonException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode items = root.path("items");

        if (!items.isArray()) {
            items = root.path("results");
        }

        List<GeoSearchResult> results = new ArrayList<>();
        if (!items.isArray()) {
            return results;
        }

        for (JsonNode item : items) {
            BigDecimal lat = firstDecimal(
                    item,
                    "location.latitude",
                    "location.y",
                    "latitude",
                    "lat"
            );
            BigDecimal lng = firstDecimal(
                    item,
                    "location.longitude",
                    "location.x",
                    "longitude",
                    "lng"
            );

            if (lat == null || lng == null) {
                continue;
            }

            results.add(
                    new GeoSearchResult(
                            firstText(item, "title", "name"),
                            firstText(
                                    item,
                                    "address",
                                    "formatted_address",
                                    "formattedAddress"
                            ),
                            firstText(
                                    item,
                                    "neighbourhood",
                                    "neighborhood"
                            ),
                            firstText(item, "city", "region"),
                            firstText(item, "category", "type"),
                            lat,
                            lng
                    )
            );
        }

        return results;
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
                    "Neshan API key was rejected."
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
