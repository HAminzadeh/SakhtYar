package com.sakhtyar.crawler.infrastructure;

import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UrlDiscovery {

    public List<String> sameHostLinks(List<String> links, String allowedHost) {
        if (allowedHost == null || allowedHost.isBlank() || links == null) {
            throw new SecurityException("Crawler discovery requires an explicit allowed host and link list");
        }
        String host = allowedHost.trim().toLowerCase();
        return links.stream()
                .filter(link -> {
                    try {
                        CrawlTargetPolicy.requireAllowedHost(URI.create(link), host);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .toList();
    }
}