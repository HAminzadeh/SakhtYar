package com.sakhtyar.geo.domain;

import java.math.BigDecimal;
import java.util.List;

public interface GeoProvider {

    /**
     * Converts a human-readable address into one or more geographic candidates.
     *
     * The current Neshan adapter uses the Geocoding API rather than the legacy
     * place-search endpoint, because the SakhtYar use case is property-address
     * resolution and the corresponding service is exposed by the Neshan panel.
     */
    List<GeoSearchResult> geocode(String address);

    ReverseGeocodeResult reverseGeocode(
            BigDecimal latitude,
            BigDecimal longitude
    );
}
