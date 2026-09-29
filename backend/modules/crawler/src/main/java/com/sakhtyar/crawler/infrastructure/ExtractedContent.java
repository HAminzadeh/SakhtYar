package com.sakhtyar.crawler.infrastructure;

import com.sakhtyar.crawler.domain.DocumentKind;
import java.util.List;

public record ExtractedContent(
        DocumentKind kind,
        String title,
        String text,
        List<String> discoveredLinks
) {
}