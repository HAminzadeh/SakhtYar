package com.sakhtyar.crawler.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlSourceRepository extends JpaRepository<CrawlSourceEntity, UUID> {
    List<CrawlSourceEntity> findAllByOrderByUpdatedAtDesc();
}