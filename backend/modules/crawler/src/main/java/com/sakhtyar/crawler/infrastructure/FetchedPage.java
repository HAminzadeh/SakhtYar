package com.sakhtyar.crawler.infrastructure;

import java.net.URI;
import java.util.Map;

public record FetchedPage(
        URI requestedUri,
        URI finalUri,
        int statusCode,
        String contentType,
        byte[] body,
        Map<String, String> headers
) {
}