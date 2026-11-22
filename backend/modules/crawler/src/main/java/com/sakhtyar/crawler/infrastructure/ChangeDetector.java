package com.sakhtyar.crawler.infrastructure;

import com.sakhtyar.crawler.domain.CrawledDocumentRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ChangeDetector {

    private final CrawledDocumentRepository repository;

    public ChangeDetector(CrawledDocumentRepository repository) {
        this.repository = repository;
    }

    public boolean isNewVersion(UUID sourceId, String canonicalUrlHash, String contentHash) {
        return !repository.existsBySourceIdAndCanonicalUrlHashAndContentHash(
                sourceId, canonicalUrlHash, contentHash
        );
    }
}