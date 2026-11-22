package com.sakhtyar.crawler.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** REAL crawler only: bounded HTTP, explicit redirect and destination checks. */
@Component
public class PageFetcher {
    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_BODY_BYTES = 8 * 1024 * 1024;
    // Never let HttpClient silently follow redirects to private or unapproved destinations.
    private final HttpClient client;

    public PageFetcher() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(15))
                .build());
    }

    // Package-private fake transport seam. Tests inject a mocked HttpClient, never real egress.
    PageFetcher(HttpClient client) {
        this.client = java.util.Objects.requireNonNull(client);
    }

    public FetchedPage fetch(String url, int timeoutMs, String userAgent) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_TESTER".equals(a.getAuthority()))) {
            throw new SecurityException("TESTER crawler egress blocked; use an isolated fake transport");
        }
        try {
            if (timeoutMs < 100 || timeoutMs > 120000) throw new SecurityException("Invalid crawler timeout");
            URI origin = URI.create(url);
            CrawlTargetPolicy.requireSyntax(origin);
            URI current = origin;
            for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
                CrawlTargetPolicy.requireSameHost(origin, current);
                CrawlTargetPolicy.requirePublicDestination(current);
                HttpRequest request = HttpRequest.newBuilder(current)
                        .GET()
                        .timeout(Duration.ofMillis(timeoutMs))
                        .header("User-Agent", userAgent)
                        .header("Accept", "text/html,application/xhtml+xml,application/pdf,text/plain,application/json,application/xml;q=0.9,*/*;q=0.5")
                        .build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream stream = response.body()) {
                    int status = response.statusCode();
                    if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                        if (redirect == MAX_REDIRECTS) throw new SecurityException("Crawler redirect limit exceeded");
                        String location = response.headers().firstValue("location")
                                .orElseThrow(() -> new SecurityException("Crawler redirect without Location"));
                        URI next = current.resolve(location).normalize();
                        CrawlTargetPolicy.requireSameHost(origin, next);
                        current = next;
                        continue;
                    }
                    if (status >= 200 && status < 300) {
                        String mediaType = response.headers().firstValue("content-type")
                                .orElse("").toLowerCase(java.util.Locale.ROOT);
                        if (!(mediaType.startsWith("text/") || mediaType.startsWith("application/pdf") ||
                                mediaType.startsWith("application/json") ||
                                mediaType.startsWith("application/xml") ||
                                mediaType.startsWith("application/xhtml+xml") ||
                                (mediaType.startsWith("application/octet-stream") &&
                                  "/robots.txt".equals(current.getPath())))) {
                            throw new SecurityException("Crawler response MIME type denied");
                        }
                    }
                    Map<String, String> headers = new LinkedHashMap<>();
                    response.headers().firstValue("etag").ifPresent(v -> headers.put("etag", v));
                    response.headers().firstValue("last-modified").ifPresent(v -> headers.put("last-modified", v));
                    response.headers().firstValue("content-length").ifPresent(v -> {
                        try {
                            if (Long.parseLong(v) > MAX_BODY_BYTES) throw new SecurityException("Crawler response too large");
                        } catch (NumberFormatException ex) {
                            throw new SecurityException("Invalid Content-Length", ex);
                        }
                    });
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = stream.read(buffer)) != -1) {
                        if (out.size() + n > MAX_BODY_BYTES) throw new SecurityException("Crawler response exceeds 8 MiB");
                        out.write(buffer, 0, n);
                    }
                    return new FetchedPage(origin, current, status,
                            response.headers().firstValue("content-type").orElse("application/octet-stream"),
                            out.toByteArray(), Map.copyOf(headers));
                }
            }
            throw new SecurityException("Unexpected crawler redirect state");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Fetch interrupted", e);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Crawler fetch failed: " + e.getClass().getSimpleName(), e);
        }
    }
}
