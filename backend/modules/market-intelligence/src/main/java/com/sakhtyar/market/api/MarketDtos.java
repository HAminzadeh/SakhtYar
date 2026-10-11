package com.sakhtyar.market.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public final class MarketDtos {
    private MarketDtos() {}

    public record ObservationRequest(
            @NotBlank @Size(max=120) String sourceCode,
            @Size(max=300) String externalId,
            @NotNull Instant observedAt,
            @Size(max=2) String countryCode,
            @Size(max=120) String province,
            @NotBlank @Size(max=120) String city,
            @Size(max=120) String district,
            @Size(max=160) String neighborhood,
            @Size(max=100) String propertyType,
            Integer constructionYear,
            @NotNull @DecimalMin("0.01") BigDecimal areaM2,
            @DecimalMin("0") BigDecimal totalPrice,
            @DecimalMin("0") BigDecimal pricePerM2,
            @NotBlank @Size(min=3,max=8) String currencyCode,
            @Size(max=40) String listingType,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @Size(max=3000) String sourceUrl,
            @DecimalMin("0") @DecimalMax("1") BigDecimal confidence,
            Map<String,Object> rawPayload
    ) {}

    public record ObservationResponse(
            UUID id,String sourceCode,String externalId,Instant observedAt,String countryCode,
            String province,String city,String district,String neighborhood,String propertyType,
            Integer constructionYear,BigDecimal areaM2,BigDecimal totalPrice,BigDecimal pricePerM2,
            String currencyCode,String listingType,BigDecimal latitude,BigDecimal longitude,
            String sourceUrl,BigDecimal confidence,Instant createdAt
    ) {}

    public record MarketSummary(
            String city,String district,String neighborhood,String propertyType,int periodDays,
            long sampleCount,BigDecimal averagePricePerM2,BigDecimal medianPricePerM2,
            BigDecimal minPricePerM2,BigDecimal maxPricePerM2,BigDecimal changePercent,
            Instant latestObservedAt,String currencyCode,String freshnessStatus
    ) {}

    public record TrendPoint(
            LocalDate day,long sampleCount,BigDecimal averagePricePerM2,BigDecimal medianPricePerM2
    ) {}

    public record ComparableResponse(
            UUID id,String sourceCode,String externalId,Instant observedAt,String city,String district,
            String neighborhood,String propertyType,Integer constructionYear,BigDecimal areaM2,
            BigDecimal totalPrice,BigDecimal pricePerM2,String currencyCode,String sourceUrl,
            BigDecimal confidence
    ) {}

    public record SnapshotResponse(
            UUID id,String city,String district,String neighborhood,String propertyType,int periodDays,
            long sampleCount,BigDecimal medianPricePerM2,BigDecimal averagePricePerM2,
            BigDecimal changePercent,String currencyCode,Instant sourceObservedAt,Instant generatedAt
    ) {}
}
