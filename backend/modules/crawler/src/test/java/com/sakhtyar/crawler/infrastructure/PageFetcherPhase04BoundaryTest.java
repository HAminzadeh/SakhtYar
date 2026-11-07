package com.sakhtyar.crawler.infrastructure;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;

class PageFetcherPhase04BoundaryTest {
    @Test void testerNeverInvokesRealHttpTransport() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("tester", null,
                List.of(new SimpleGrantedAuthority("ROLE_TESTER"))));
        try {
            assertThrows(SecurityException.class, () -> new PageFetcher().fetch("https://example.org/", 1000, "SakhtYar-TEST"));
        } finally { SecurityContextHolder.clearContext(); }
    }
}
