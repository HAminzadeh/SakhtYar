package com.sakhtyar.crawler.infrastructure;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class UrlCanonicalizer {

    private UrlCanonicalizer() {}

    public static String canonicalize(String rawUrl) {
        try {
            if (rawUrl == null || rawUrl.isBlank() || rawUrl.length() > 3000)
                throw new SecurityException("Invalid crawler URL length");
            URI uri = new URI(rawUrl.trim()).normalize();
            // A fragment is client-side only; strip it before crawler-target validation.
            // Parsing the complete URL first still rejects malformed URI syntax.
            if (uri.getRawFragment() != null) {
                String normalizedUrl = uri.toString();
                uri = new URI(normalizedUrl.substring(0, normalizedUrl.indexOf('#'))).normalize();
            }
            CrawlTargetPolicy.requireSyntax(uri);
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
                port = -1;
            }
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                path = "/";
            }
            URI canonical = new URI(scheme, uri.getUserInfo(), host, port, path, uri.getQuery(), null);
            return canonical.toASCIIString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL: " + rawUrl, e);
        }
    }
}