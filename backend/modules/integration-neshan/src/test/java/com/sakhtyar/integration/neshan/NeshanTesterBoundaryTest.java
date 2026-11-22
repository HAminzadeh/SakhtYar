package com.sakhtyar.integration.neshan;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;

class NeshanTesterBoundaryTest {
    @Test void testerCannotInvokeExternalMapProvider() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("tester",null,
                List.of(new SimpleGrantedAuthority("ROLE_TESTER"))));
        try {
            var provider=new NeshanGeoProvider(null,null,null);
            assertThrows(SecurityException.class,()->provider.geocode("synthetic"));
            assertThrows(SecurityException.class,()->provider.reverseGeocode(null,null));
        } finally { SecurityContextHolder.clearContext(); }
    }
}
