package com.sakhtyar.crawler.infrastructure;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UrlDiscoveryPhase04Test {
    @Test void onlySameHostHttpLinksSurvive() {
        List<String> result = new UrlDiscovery().sameHostLinks(List.of(
            "https://example.org/page", "http://example.org/a", "http://127.0.0.1/private",
            "https://other.org/page", "javascript:alert(1)",
            "https://user:password@example.org/"), "example.org");
        assertEquals(List.of("https://example.org/page", "http://example.org/a"), result);
    }
}
