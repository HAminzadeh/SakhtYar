package com.sakhtyar.crawler.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawledDocumentRepository extends JpaRepository<CrawledDocumentEntity, UUID> {
    boolean existsBySourceIdAndCanonicalUrlHashAndContentHash(UUID sourceId, String canonicalUrlHash, String contentHash);
    Optional<CrawledDocumentEntity> findTopBySourceIdAndCanonicalUrlHashOrderByObservedAtDesc(UUID sourceId, String canonicalUrlHash);
    List<CrawledDocumentEntity> findByJobIdOrderByObservedAtDesc(UUID jobId);
}