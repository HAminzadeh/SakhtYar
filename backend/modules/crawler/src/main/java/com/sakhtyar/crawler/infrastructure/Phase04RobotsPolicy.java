package com.sakhtyar.crawler.infrastructure;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Per-run robots.txt policy. Fetching remains in PageFetcher (no custom network transport).
 * Unknown responses and malformed/oversized robots are denied, never silently bypassed.
 */
public final class Phase04RobotsPolicy {
    private static final int MAX_ROBOTS_BYTES = 256 * 1024;
    private record Rule(boolean allow, String path, Pattern pattern, int precedence) {}
    private final List<Rule> rules;
    private final boolean denyAll;

    private Phase04RobotsPolicy(List<Rule> rules, boolean denyAll) {
        this.rules = List.copyOf(rules);
        this.denyAll = denyAll;
    }
    public static Phase04RobotsPolicy allowAll() { return new Phase04RobotsPolicy(List.of(), false); }
    private static Phase04RobotsPolicy denyAll() { return new Phase04RobotsPolicy(List.of(), true); }

    public static Phase04RobotsPolicy fromResponse(FetchedPage response, String userAgent) {
        if (response == null) throw new SecurityException("robots.txt unavailable");
        int status = response.statusCode();
        if (status == 404 || status == 410) return allowAll();
        if (status == 401 || status == 403) return denyAll();
        if (status != 200) throw new SecurityException("robots.txt response is not trusted");
        if (response.body() == null || response.body().length > MAX_ROBOTS_BYTES)
            throw new SecurityException("robots.txt is too large");
        String contentType = response.contentType().toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("text/plain") && !contentType.startsWith("application/octet-stream"))
            throw new SecurityException("robots.txt MIME rejected");
        return parse(new String(response.body(), StandardCharsets.UTF_8), userAgent);
    }

    public static Phase04RobotsPolicy parse(String robots, String agent) {
        if (robots == null || agent == null || agent.isBlank() || robots.length() > MAX_ROBOTS_BYTES)
            throw new SecurityException("Invalid robots source or crawler identity");
        String ua = agent.toLowerCase(Locale.ROOT);
        List<Rule> selected = new ArrayList<>();
        int bestAgentLength = -1;
        int currentAgentLen = -1;
        boolean previousWasUserAgent = false;
        for (String raw : robots.split("\\r?\\n", -1)) {
            String line = raw.split("#", 2)[0].trim();
            if (line.isEmpty()) { previousWasUserAgent = false; continue; }
            int colon = line.indexOf(':');
            if (colon < 1) continue;
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (name.equals("user-agent")) {
                int match = (value.equals("*") ? 0 :
                    (ua.contains(value.toLowerCase(Locale.ROOT)) ? value.length() : -1));
                if (previousWasUserAgent) currentAgentLen = Math.max(currentAgentLen, match);
                else currentAgentLen = match;
                previousWasUserAgent = true;
                if (currentAgentLen > bestAgentLength) {
                    bestAgentLength = currentAgentLen;
                    selected.clear();
                }
                continue;
            }
            previousWasUserAgent = false;
            if (currentAgentLen < 0 || currentAgentLen != bestAgentLength) continue;
            if (!name.equals("allow") && !name.equals("disallow")) continue;
            if (value.isEmpty()) continue;
            if (!value.startsWith("/") || value.length() > 2048)
                throw new SecurityException("Invalid robots path");
            boolean exactEnd = value.endsWith("$");
            String patternText = exactEnd ? value.substring(0, value.length() - 1) : value;
            StringBuilder expression = new StringBuilder("^");
            String[] parts = patternText.split("\\*", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) expression.append(".*");
                expression.append(Pattern.quote(parts[i]));
            }
            expression.append(exactEnd ? "$" : ".*");
            String regex = expression.toString();
            selected.add(new Rule(name.equals("allow"), value, Pattern.compile(regex),
                    value.replace("*", "").replace("$", "").length()));
        }
        return new Phase04RobotsPolicy(selected, false);
    }

    public void requireAllowed(URI url) {
        CrawlTargetPolicy.requireSyntax(url);
        if (denyAll) throw new SecurityException("Crawler disallowed by robots.txt");
        String path = url.getRawPath() == null || url.getRawPath().isEmpty() ? "/" : url.getRawPath();
        if (url.getRawQuery() != null) path += "?" + url.getRawQuery();
        Rule winner = null;
        for (Rule r : rules)
            if (r.pattern.matcher(path).matches() &&
                (winner == null || r.precedence > winner.precedence ||
                (r.precedence == winner.precedence && r.allow && !winner.allow))) winner = r;
        if (winner != null && !winner.allow)
            throw new SecurityException("Crawler path disallowed by robots.txt");
    }
}
