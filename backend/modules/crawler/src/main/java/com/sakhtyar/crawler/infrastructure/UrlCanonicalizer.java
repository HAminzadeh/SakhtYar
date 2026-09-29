package com.sakhtyar.crawler.infrastructure;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class UrlCanonicalizer {

    private UrlCanonicalizer() {}

    public static String canonicalize(String rawUrl) {
        try {
            URI uri = new URI(rawUrl.trim()).normalize();
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
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