package com.sakhtyar.crawler.infrastructure;

import java.net.InetAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrawlTargetPolicyTest {
    @Test void rejectsMetadataLoopbackPrivateAndUnsafeSchemes() throws Exception {
        assertThrows(SecurityException.class, () -> CrawlTargetPolicy.requirePublicDestination(URI.create("http://127.0.0.1:8080")));
        assertThrows(SecurityException.class, () -> CrawlTargetPolicy.requirePublicDestination(URI.create("http://169.254.169.254/latest/meta-data")));
        assertThrows(SecurityException.class, () -> CrawlTargetPolicy.requirePublicDestination(URI.create("http://10.1.2.3")));
        assertThrows(SecurityException.class, () -> CrawlTargetPolicy.requireSyntax(URI.create("file:///etc/passwd")));
        assertThrows(SecurityException.class, () -> CrawlTargetPolicy.requireSyntax(URI.create("http://user:pass@public.example/")));
        assertFalse(CrawlTargetPolicy.isPublicAddress(InetAddress.getByAddress(new byte[]{(byte)100, 100, 5, 5})));
        assertTrue(CrawlTargetPolicy.isPublicAddress(InetAddress.getByAddress(new byte[]{8, 8, 8, 8})));
    }
    @Test void rejectsCrossHostRedirectAndHttpsDowngrade() {
        assertThrows(SecurityException.class, () -> CrawlTargetPolicy.requireSameHost(URI.create("https://example.org/a"), URI.create("https://other.org/b")));
        assertThrows(SecurityException.class, () -> CrawlTargetPolicy.requireSameHost(URI.create("https://example.org/a"), URI.create("http://example.org/b")));
        assertThrows(SecurityException.class, () -> CrawlTargetPolicy.requireAllowedHost(URI.create("https://sub.example.org/a"), "example.org"));
        assertDoesNotThrow(() -> CrawlTargetPolicy.requireSameHost(URI.create("http://example.org/a"), URI.create("https://example.org/b")));
    }
}
