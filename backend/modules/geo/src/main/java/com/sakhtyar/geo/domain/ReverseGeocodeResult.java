package com.sakhtyar.geo.domain;

public record ReverseGeocodeResult(
        String formattedAddress,
        String province,
        String city,
        String district,
        String neighborhood,
        String routeName,
        String place
) {
}
