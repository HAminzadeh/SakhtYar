package com.sakhtyar.crawler.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class UrlCanonicalizerTest {

    @Test
    void removesFragmentAndDefaultHttpsPort() {
        assertEquals(
                "https://example.com/path?a=1",
                UrlCanonicalizer.canonicalize("HTTPS://Example.COM:443/path?a=1#section")
        );
    }

    @Test
    void normalizesEmptyPath() {
        assertEquals(
                "https://example.com/",
                UrlCanonicalizer.canonicalize("https://example.com")
        );
    }
}