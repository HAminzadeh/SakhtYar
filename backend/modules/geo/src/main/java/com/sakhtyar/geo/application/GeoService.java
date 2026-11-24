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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Read-only GIS boundary. No synthetic TEST/QA actor may call real services. */
@Service
public class GeoService {
    private final ObjectProvider<GeoProvider> provider;
    private final GeoSpatialRepository spatialRepository;

    public GeoService(ObjectProvider<GeoProvider> provider, GeoSpatialRepository spatialRepository) {
        this.provider = provider;
        this.spatialRepository = spatialRepository;
    }

    public boolean providerAvailable() {
        requireReader();
        GeoProvider value = provider.getIfAvailable();
        return value != null && value.configured();
    }

    public List<GeoSearchResult> geocode(String address) {
        requireReader();
        String cleaned = invalidAddressToHttp(address);
        List<GeoSearchResult> results = requireProvider().geocode(cleaned);
        if (results == null || results.size() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Geocoding result set is invalid.");
        }
        for (GeoSearchResult item : results) {
            if (item == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid geocoding result.");
            try { GeoInputPolicy.point(item.latitude(), item.longitude()); }
            catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid coordinates in provider result.");
            }
        }
        return List.copyOf(results);
    }

    public ReverseGeocodeResult reverseGeocode(BigDecimal latitude, BigDecimal longitude) {
        requireReader();
        pointOrHttp(latitude, longitude);
        ReverseGeocodeResult result = requireProvider().reverseGeocode(latitude, longitude);
        if (result == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No reverse-geocoding result.");
        return result;
    }

    @Transactional(readOnly = true)
    public List<NearbyProperty> nearbyProperties(BigDecimal latitude, BigDecimal longitude, int radiusMeters) {
        requireReader();
        pointOrHttp(latitude, longitude);
        try { GeoInputPolicy.radius(radiusMeters); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage()); }
        // Repository independently checks auth and filters accessible REAL cases in SQL.
        return spatialRepository.findNearbyProperties(latitude, longitude, radiusMeters);
    }

    private GeoProvider requireProvider() {
        GeoProvider value = provider.getIfAvailable();
        if (value == null || !value.configured())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Geocoding service is not configured.");
        return value;
    }

    private static String invalidAddressToHttp(String address) {
        try { return GeoInputPolicy.address(address); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage()); }
    }

    private static void pointOrHttp(BigDecimal latitude, BigDecimal longitude) {
        try { GeoInputPolicy.point(latitude, longitude); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage()); }
    }

    private static void requireReader() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null || auth.getName().isBlank() ||
                auth.getAuthorities() == null ||
                auth.getAuthorities().stream().anyMatch(x -> "ROLE_TESTER".equals(x.getAuthority())) ||
                auth.getAuthorities().stream().noneMatch(x -> "CASE_READ".equals(x.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Geo access denied.");
        }
    }
}
