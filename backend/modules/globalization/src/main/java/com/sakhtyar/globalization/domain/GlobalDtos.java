package com.sakhtyar.globalization.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class GlobalDtos {
    private GlobalDtos(){}

    public record LanguageItem(UUID id,String code,String bcp47Tag,String nameEn,String nameNative,String direction){}
    public record CurrencyItem(UUID id,String code,String nameEn,String symbol,Integer minorUnit,boolean iso4217){}
    public record CountryItem(UUID id,String isoAlpha2,String isoAlpha3,String nameEn,String nameNative,UUID defaultCurrencyId){}
    public record DivisionItem(UUID id,UUID countryId,UUID parentId,String code,String type,String nameEn,String nameNative,int level){}
    public record CityItem(UUID id,UUID countryId,UUID divisionId,long geonamesId,String name,String asciiName,String timezone,Long population){}
    public record JurisdictionContext(UUID caseId,UUID propertyId,UUID countryId,UUID divisionId,UUID cityId,String countryCode,String divisionCode,String cityName){}
    public record Preference(
            UUID userId,UUID languageId,UUID countryId,UUID currencyId,
            String languageCode,String countryCode,String currencyCode,
            String timezone,String theme,String dateFormat,String numberFormat,Integer firstDayOfWeek
    ){}
    public record PreferenceUpdate(
            UUID languageId,UUID countryId,UUID currencyId,
            String timezone,String theme,String dateFormat,String numberFormat,Integer firstDayOfWeek
    ){}
    public record FxRequest(
            UUID baseCurrencyId,UUID quoteCurrencyId,BigDecimal rate,
            String sourceLabel,String sourceUrl,Instant observedAt,BigDecimal confidence
    ){}
}