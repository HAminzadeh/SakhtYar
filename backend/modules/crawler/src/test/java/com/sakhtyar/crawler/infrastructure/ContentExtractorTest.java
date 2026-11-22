package com.sakhtyar.crawler.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContentExtractorTest {

    private final ContentExtractor extractor = new ContentExtractor();

    @Test
    void extractsTitleTextAndLinksFromHtml() {
        String html = """
                <html>
                  <head><title>Municipality Rule</title><style>.x{display:none}</style></head>
                  <body>
                    <h1>Building rule</h1>
                    <a href="/rules/2">Next</a>
                    <script>ignored()</script>
                  </body>
                </html>
                """;

        FetchedPage page = new FetchedPage(
                URI.create("https://example.com/rules/1"),
                URI.create("https://example.com/rules/1"),
                200,
                "text/html; charset=UTF-8",
                html.getBytes(StandardCharsets.UTF_8),
                Map.of()
        );

        ExtractedContent result = extractor.extract(page);

        assertEquals("Municipality Rule", result.title());
        assertTrue(result.text().contains("Building rule"));
        assertFalse(result.text().contains("ignored()"));
        assertEquals("https://example.com/rules/2", result.discoveredLinks().getFirst());
    }
}