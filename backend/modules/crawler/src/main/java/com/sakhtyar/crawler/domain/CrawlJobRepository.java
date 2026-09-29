package com.sakhtyar.crawler.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlJobRepository extends JpaRepository<CrawlJobEntity, UUID> {
    List<CrawlJobEntity> findBySourceIdOrderByCreatedAtDesc(UUID sourceId);
}