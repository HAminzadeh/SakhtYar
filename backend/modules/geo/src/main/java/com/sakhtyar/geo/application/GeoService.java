package com.sakhtyar.geo.application;

import com.sakhtyar.geo.domain.GeoProvider;
import com.sakhtyar.geo.domain.GeoSearchResult;
import com.sakhtyar.geo.domain.NearbyProperty;
import com.sakhtyar.geo.domain.ReverseGeocodeResult;
import com.sakhtyar.geo.infrastructure.GeoSpatialRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GeoService {

    private final ObjectProvider<GeoProvider> provider;
    private final GeoSpatialRepository spatialRepository;

    public GeoService(
            ObjectProvider<GeoProvider> provider,
            GeoSpatialRepository spatialRepository
    ) {
        this.provider = provider;
        this.spatialRepository = spatialRepository;
    }

    public boolean providerAvailable() {
        return provider.getIfAvailable() != null;
    }

    public List<GeoSearchResult> search(
            String term,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        if (term == null || term.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Search term is required."
            );
        }

        return requireProvider().search(
                term.trim(),
                latitude,
                longitude
        );
    }

    public ReverseGeocodeResult reverseGeocode(
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        return requireProvider().reverseGeocode(latitude, longitude);
    }

    public List<NearbyProperty> nearbyProperties(
            BigDecimal latitude,
            BigDecimal longitude,
            int radiusMeters
    ) {
        if (radiusMeters < 1 || radiusMeters > 20_000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "radiusMeters must be between 1 and 20000."
            );
        }

        return spatialRepository.findNearbyProperties(
                latitude,
                longitude,
                radiusMeters
        );
    }

    private GeoProvider requireProvider() {
        GeoProvider value = provider.getIfAvailable();
        if (value == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Map search provider is not configured. Enable Neshan and set NESHAN_SERVICE_API_KEY."
            );
        }
        return value;
    }
}
