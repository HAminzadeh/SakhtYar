package com.sakhtyar.geo.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record NearbyProperty(
        UUID caseId,
        String caseTitle,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        double distanceMeters
) {
}
