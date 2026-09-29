package com.sakhtyar.crawler.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "crawl_source")
public class CrawlSourceEntity {

    @Id
    private UUID id;

    @Column(name = "knowledge_source_id", nullable = false)
    private UUID knowledgeSourceId;

    @Column(nullable = false, length = 300)
    private String name;

    @Column(name = "seed_url", nullable = false, length = 3000)
    private String seedUrl;

    @Column(name = "allowed_host", length = 500)
    private String allowedHost;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "max_depth", nullable = false)
    private int maxDepth;

    @Column(name = "max_pages", nullable = false)
    private int maxPages;

    @Column(name = "request_delay_ms", nullable = false)
    private int requestDelayMs;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs;

    @Column(name = "respect_robots", nullable = false)
    private boolean respectRobots;

    @Column(name = "user_agent", nullable = false, length = 500)
    private String userAgent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_by", nullable = false, length = 150)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CrawlSourceEntity() {}

    public CrawlSourceEntity(
            UUID id, UUID knowledgeSourceId, String name, String seedUrl, String allowedHost,
            boolean enabled, int maxDepth, int maxPages, int requestDelayMs, int timeoutMs,
            boolean respectRobots, String userAgent, Map<String, Object> metadata,
            String createdBy, Instant now
    ) {
        this.id = id;
        this.knowledgeSourceId = knowledgeSourceId;
        this.name = name;
        this.seedUrl = seedUrl;
        this.allowedHost = allowedHost;
        this.enabled = enabled;
        this.maxDepth = maxDepth;
        this.maxPages = maxPages;
        this.requestDelayMs = requestDelayMs;
        this.timeoutMs = timeoutMs;
        this.respectRobots = respectRobots;
        this.userAgent = userAgent;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            String name, String seedUrl, String allowedHost, boolean enabled,
            int maxDepth, int maxPages, int requestDelayMs, int timeoutMs,
            boolean respectRobots, String userAgent, Map<String, Object> metadata, Instant now
    ) {
        this.name = name;
        this.seedUrl = seedUrl;
        this.allowedHost = allowedHost;
        this.enabled = enabled;
        this.maxDepth = maxDepth;
        this.maxPages = maxPages;
        this.requestDelayMs = requestDelayMs;
        this.timeoutMs = timeoutMs;
        this.respectRobots = respectRobots;
        this.userAgent = userAgent;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getKnowledgeSourceId() { return knowledgeSourceId; }
    public String getName() { return name; }
    public String getSeedUrl() { return seedUrl; }
    public String getAllowedHost() { return allowedHost; }
    public boolean isEnabled() { return enabled; }
    public int getMaxDepth() { return maxDepth; }
    public int getMaxPages() { return maxPages; }
    public int getRequestDelayMs() { return requestDelayMs; }
    public int getTimeoutMs() { return timeoutMs; }
    public boolean isRespectRobots() { return respectRobots; }
    public String getUserAgent() { return userAgent; }
    public Map<String, Object> getMetadata() { return metadata == null ? Map.of() : Map.copyOf(metadata); }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}