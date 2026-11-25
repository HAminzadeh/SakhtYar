package com.sakhtyar.integration.neshan;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Prevent SSRF and accidental exposure of paid API credentials to alternate hosts. */
final class NeshanDestinationPolicy {
    private NeshanDestinationPolicy() { }

    static void requireOfficialHttps(NeshanProperties properties) {
        URI uri;
        try {
            uri = URI.create(properties.baseUrl());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw disabled();
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) ||
                !"api.neshan.org".equalsIgnoreCase(uri.getHost()) ||
                (uri.getPort() != -1 && uri.getPort() != 443) ||
                uri.getUserInfo() != null || uri.getQuery() != null ||
                uri.getFragment() != null ||
                (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !"/".equals(uri.getRawPath()))) {
            throw disabled();
        }
    }

    private static ResponseStatusException disabled() {
        // Do not echo untrusted URL (possibly containing credentials) in any error.
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Neshan endpoint configuration is not approved.");
    }
}
