package com.sakhtyar.geo.domain;

import java.math.BigDecimal;
import java.util.List;

/** Pluggable geocoding adapter; external HTTP must be barred for TEST actors. */
public interface GeoProvider {
    List<GeoSearchResult> geocode(String address);
    ReverseGeocodeResult reverseGeocode(BigDecimal latitude, BigDecimal longitude);

    /** Registered is not necessarily configured (e.g. API key absent). */
    default boolean configured() { return true; }
}
