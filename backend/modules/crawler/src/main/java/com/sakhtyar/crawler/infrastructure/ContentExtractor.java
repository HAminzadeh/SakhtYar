package com.sakhtyar.crawler.infrastructure;

import com.sakhtyar.crawler.domain.DocumentKind;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ContentExtractor {

    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern LINK = Pattern.compile("(?is)<a\\s+[^>]*href\\s*=\\s*[\"']([^\"'#]+)[\"']");
    private static final Pattern SCRIPT = Pattern.compile("(?is)<(script|style|noscript)[^>]*>.*?</\\1>");
    private static final Pattern TAG = Pattern.compile("(?is)<[^>]+>");

    public ExtractedContent extract(FetchedPage page) {
        DocumentKind kind = classify(page.contentType(), page.finalUri().toString());
        if (kind == DocumentKind.PDF) {
            return new ExtractedContent(kind, fileName(page.finalUri()), "", List.of());
        }

        String body = new String(page.body(), StandardCharsets.UTF_8);
        if (kind == DocumentKind.HTML || kind == DocumentKind.XML) {
            String title = extractTitle(body);
            List<String> links = kind == DocumentKind.HTML ? extractLinks(page.finalUri(), body) : List.of();
            String text = decodeEntities(TAG.matcher(SCRIPT.matcher(body).replaceAll(" ")).replaceAll(" "))
                    .replaceAll("\\s+", " ")
                    .trim();
            return new ExtractedContent(kind, title, text, links);
        }

        if (kind == DocumentKind.TEXT || kind == DocumentKind.JSON) {
            return new ExtractedContent(kind, fileName(page.finalUri()), body.trim(), List.of());
        }

        return new ExtractedContent(kind, fileName(page.finalUri()), "", List.of());
    }

    private static DocumentKind classify(String contentType, String url) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String lowerUrl = url.toLowerCase(Locale.ROOT);
        if (ct.contains("pdf") || lowerUrl.endsWith(".pdf")) return DocumentKind.PDF;
        if (ct.contains("html") || ct.contains("xhtml")) return DocumentKind.HTML;
        if (ct.contains("json")) return DocumentKind.JSON;
        if (ct.contains("xml")) return DocumentKind.XML;
        if (ct.startsWith("text/")) return DocumentKind.TEXT;
        return DocumentKind.UNKNOWN;
    }

    private static String extractTitle(String html) {
        Matcher matcher = TITLE.matcher(html);
        return matcher.find()
                ? decodeEntities(TAG.matcher(matcher.group(1)).replaceAll(" ")).replaceAll("\\s+", " ").trim()
                : null;
    }

    private static List<String> extractLinks(URI base, String html) {
        LinkedHashSet<String> links = new LinkedHashSet<>();
        Matcher matcher = LINK.matcher(html);
        while (matcher.find() && links.size() < 500) {
            try {
                URI resolved = base.resolve(matcher.group(1).trim());
                String scheme = resolved.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    links.add(resolved.toString());
                }
            } catch (Exception ignored) {
            }
        }
        return List.copyOf(links);
    }

    private static String fileName(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank() || path.endsWith("/")) return uri.getHost();
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String decodeEntities(String text) {
        return text
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }
}