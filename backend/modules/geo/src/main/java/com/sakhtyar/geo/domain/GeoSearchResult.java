package com.sakhtyar.geo.domain;

import java.math.BigDecimal;

public record GeoSearchResult(
        String title,
        String address,
        String neighborhood,
        String city,
        String category,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
