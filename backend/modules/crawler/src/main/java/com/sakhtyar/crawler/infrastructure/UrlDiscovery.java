package com.sakhtyar.crawler.infrastructure;

import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UrlDiscovery {

    public List<String> sameHostLinks(List<String> links, String allowedHost) {
        if (allowedHost == null || allowedHost.isBlank()) {
            return links;
        }
        String host = allowedHost.trim().toLowerCase();
        return links.stream()
                .filter(link -> {
                    try {
                        String candidate = URI.create(link).getHost();
                        return candidate != null && candidate.equalsIgnoreCase(host);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .toList();
    }
}