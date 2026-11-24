package com.sakhtyar.geo.application;

import com.sakhtyar.geo.domain.*;
import com.sakhtyar.geo.infrastructure.GeoSpatialRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Phase14GeoServiceTest {
    private final ObjectProvider<GeoProvider> providers = mock(ObjectProvider.class);
    private final GeoSpatialRepository spatial = mock(GeoSpatialRepository.class);
    private final GeoService service = new GeoService(providers, spatial);

    @AfterEach void clean() { SecurityContextHolder.clearContext(); }
    private void login(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "synthetic-user", "unused", AuthorityUtils.createAuthorityList(roles)));
    }

    @Test void deniesUnauthenticatedWithoutCallingAnyDependency() {
        assertThrows(ResponseStatusException.class, () -> service.providerAvailable());
        assertThrows(ResponseStatusException.class, () -> service.geocode("Tehran"));
        assertThrows(ResponseStatusException.class, () -> service.nearbyProperties(BigDecimal.ZERO, BigDecimal.ZERO, 100));
        verifyNoInteractions(providers, spatial);
    }

    @Test void deniesTesterEvenWhenCasePermissionWasAccidentallyGranted() {
        login("ROLE_TESTER", "CASE_READ");
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.reverseGeocode(BigDecimal.ONE, BigDecimal.ONE)).getStatusCode());
        verifyNoInteractions(providers, spatial);
    }

    @Test void deniesOrdinaryActorWithoutReadAuthority() {
        login("ROLE_READ_ONLY");
        assertThrows(ResponseStatusException.class, () -> service.geocode("Tehran"));
        verifyNoInteractions(providers, spatial);
    }

    @Test void reportsUnconfiguredProviderInsteadOfFalseReady() {
        login("CASE_READ");
        GeoProvider external = mock(GeoProvider.class);
        when(providers.getIfAvailable()).thenReturn(external);
        when(external.configured()).thenReturn(false);
        assertFalse(service.providerAvailable());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> service.geocode("Tehran")).getStatusCode());
        verify(external, never()).geocode(anyString());
    }

    @Test void rejectsBadInputsBeforeNetworkOrDatabase() {
        login("CASE_READ");
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.geocode("\n")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.reverseGeocode(new BigDecimal("91"), BigDecimal.ZERO)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.nearbyProperties(BigDecimal.ZERO, BigDecimal.ZERO, 0)).getStatusCode());
        verifyNoInteractions(providers, spatial);
    }

    @Test void validNearbyUsesOnlyRepositoryForResults() {
        login("CASE_READ");
        BigDecimal lat = new BigDecimal("35.6892"), lon = new BigDecimal("51.3890");
        when(spatial.findNearbyProperties(lat, lon, 1000)).thenReturn(List.of());
        assertEquals(List.of(), service.nearbyProperties(lat, lon, 1000));
        verify(spatial).findNearbyProperties(lat, lon, 1000);
        verifyNoInteractions(providers);
    }

    @Test void rejectsMalformedProviderCoordinates() {
        login("CASE_READ");
        GeoProvider external = mock(GeoProvider.class);
        when(providers.getIfAvailable()).thenReturn(external);
        when(external.configured()).thenReturn(true);
        when(external.geocode("Tehran")).thenReturn(List.of(
                new GeoSearchResult("bad", "bad", null, null, null,
                        new BigDecimal("100"), BigDecimal.ONE)));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ResponseStatusException.class,
                () -> service.geocode("Tehran")).getStatusCode());
    }

    @Test void acceptsValidProviderResponseWithoutChangingCoordinates() {
        login("CASE_READ");
        GeoProvider external = mock(GeoProvider.class);
        when(providers.getIfAvailable()).thenReturn(external);
        when(external.configured()).thenReturn(true);
        var one = new GeoSearchResult("Tehran", "Tehran", null, "Tehran", null,
                new BigDecimal("35.6892"), new BigDecimal("51.3890"));
        when(external.geocode("Tehran")).thenReturn(List.of(one));
        assertEquals(one, service.geocode(" Tehran ").get(0));
    }
}
