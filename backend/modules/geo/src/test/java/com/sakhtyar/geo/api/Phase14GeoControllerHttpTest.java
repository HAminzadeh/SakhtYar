package com.sakhtyar.geo.api;

import com.sakhtyar.geo.application.GeoService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class Phase14GeoControllerHttpTest {
    @Test void routeCompatibilityStatusGeocodeReverseNearby() throws Exception {
        var service = mock(GeoService.class);
        when(service.providerAvailable()).thenReturn(false);
        when(service.geocode("Tehran")).thenReturn(List.of());
        when(service.nearbyProperties(new BigDecimal("35"), new BigDecimal("51"), 1000)).thenReturn(List.of());
        var mvc = MockMvcBuilders.standaloneSetup(new GeoController(service)).build();
        mvc.perform(get("/api/v1/geo/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.providerAvailable").value(false))
                .andExpect(jsonPath("$.coordinateReferenceSystem").value("EPSG:4326"));
        mvc.perform(get("/api/v1/geo/geocode").param("address", "Tehran"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/geo/search").param("term", "Tehran"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/geo/nearby-properties").param("lat", "35").param("lng", "51"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/geo/reverse").param("lat", "35"))
                .andExpect(status().isBadRequest());
        verify(service, times(2)).geocode("Tehran");
    }
}
