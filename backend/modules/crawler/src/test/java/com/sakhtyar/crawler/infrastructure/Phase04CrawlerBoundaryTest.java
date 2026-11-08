package com.sakhtyar.crawler.infrastructure;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase04CrawlerBoundaryTest {
    @Test void discoveryCannotFailOpenWithoutAnAllowlistedHost() {
        assertThrows(SecurityException.class, () -> new UrlDiscovery().sameHostLinks(
            List.of("https://example.org/"), ""));
        assertThrows(SecurityException.class, () -> new UrlDiscovery().sameHostLinks(
            List.of("https://example.org/"), null));
    }
    @Test void canonicalizerRejectsCredentialsAndNonHttpScheme() {
        assertThrows(SecurityException.class, () -> UrlCanonicalizer.canonicalize("file:///tmp/x"));
        assertThrows(SecurityException.class, () -> UrlCanonicalizer.canonicalize("https://user:pass@example.org/x"));
        assertEquals("https://example.org/", UrlCanonicalizer.canonicalize("https://example.org"));
    }
}
