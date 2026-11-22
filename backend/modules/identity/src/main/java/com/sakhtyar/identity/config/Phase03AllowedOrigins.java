package com.sakhtyar.identity.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

/** Fail-closed allowed-origin policy for credentialed browser requests. */
public final class Phase03AllowedOrigins {
    private Phase03AllowedOrigins() {}

    public static List<String> parse(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank())
            throw new IllegalArgumentException("At least one exact CORS origin is required");
        List<String> values = Arrays.stream(commaSeparated.split(",", -1))
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        if (values.isEmpty()) throw new IllegalArgumentException("Empty CORS origins");
        for (String origin : values) {
            try {
                URI uri = new URI(origin);
                String scheme = uri.getScheme();
                String host = uri.getHost();
                if (scheme == null || host == null || uri.getRawUserInfo() != null ||
                        uri.getRawQuery() != null || uri.getRawFragment() != null ||
                        (uri.getRawPath() != null && !uri.getRawPath().isEmpty()) ||
                        uri.getPort() > 65535 || uri.getPort() == 0 ||
                        !("https".equalsIgnoreCase(scheme) ||
                            ("http".equalsIgnoreCase(scheme) &&
                             ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host))))) {
                    throw new IllegalArgumentException("Unsafe credentialed CORS origin: " + origin);
                }
            } catch (URISyntaxException ex) {
                throw new IllegalArgumentException("Invalid credentialed CORS origin", ex);
            }
        }
        return values;
    }
}
