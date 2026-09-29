package com.sakhtyar.crawler.infrastructure;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PageFetcher {

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public FetchedPage fetch(String url, int timeoutMs, String userAgent) {
        try {
            URI uri = URI.create(url);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", userAgent)
                    .header("Accept", "text/html,application/xhtml+xml,application/pdf,text/plain,application/json,application/xml;q=0.9,*/*;q=0.5")
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            Map<String, String> headers = new LinkedHashMap<>();
            response.headers().firstValue("etag").ifPresent(v -> headers.put("etag", v));
            response.headers().firstValue("last-modified").ifPresent(v -> headers.put("last-modified", v));

            return new FetchedPage(
                    uri,
                    response.uri(),
                    response.statusCode(),
                    response.headers().firstValue("content-type").orElse("application/octet-stream"),
                    response.body(),
                    Map.copyOf(headers)
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Fetch interrupted for " + url, e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch " + url + ": " + e.getMessage(), e);
        }
    }
}