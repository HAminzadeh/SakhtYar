package com.sakhtyar.crawler.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlUrlRepository extends JpaRepository<CrawlUrlEntity, UUID> {
    boolean existsByJobIdAndUrlHash(UUID jobId, String urlHash);
}