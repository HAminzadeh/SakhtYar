package com.sakhtyar.geo.domain;

import java.math.BigDecimal;
import java.util.List;

public interface GeoProvider {

    List<GeoSearchResult> search(
            String term,
            BigDecimal latitude,
            BigDecimal longitude
    );

    ReverseGeocodeResult reverseGeocode(
            BigDecimal latitude,
            BigDecimal longitude
    );
}
