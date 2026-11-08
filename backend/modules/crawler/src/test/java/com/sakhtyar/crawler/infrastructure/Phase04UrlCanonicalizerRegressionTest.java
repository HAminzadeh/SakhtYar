package com.sakhtyar.crawler.infrastructure;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Regression for fragment-stripping before syntax validation; no network or DB. */
class Phase04UrlCanonicalizerRegressionTest {
    @Test void removesFragmentWithoutRelaxingTargetGuards() {
        assertEquals("https://example.com/path?a=1",
            UrlCanonicalizer.canonicalize("HTTPS://Example.COM:443/path?a=1#section"));
        assertEquals("http://example.com/a",
            UrlCanonicalizer.canonicalize("http://example.com:80/a#section"));
        assertEquals("https://example.com/",
            UrlCanonicalizer.canonicalize("https://example.com/#"));
    }

    @Test void rejectsUnsafeTargetsAfterFragmentRemoval() {
        assertThrows(SecurityException.class,
            () -> UrlCanonicalizer.canonicalize("https://user:pass@example.com/a#section"));
        assertThrows(SecurityException.class,
            () -> UrlCanonicalizer.canonicalize("http://localhost/hidden#section"));
        assertThrows(SecurityException.class,
            () -> UrlCanonicalizer.canonicalize("file:///etc/passwd#section"));
        assertThrows(SecurityException.class,
            () -> UrlCanonicalizer.canonicalize("https://example.com:0/a#section"));
    }

    @Test void rejectsMalformedAndOversizedUrls() {
        assertThrows(IllegalArgumentException.class,
            () -> UrlCanonicalizer.canonicalize("https://example.com/a b#frag"));
        assertThrows(SecurityException.class,
            () -> UrlCanonicalizer.canonicalize("https://example.com/" + "x".repeat(3000)));
    }
}
