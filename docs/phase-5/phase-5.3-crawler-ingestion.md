# Phase 5.3 â€” Crawler & Ingestion

## Purpose
Phase 5.3 introduces a controlled ingestion pipeline for official, semi-official, professional and other configured sources.

## Architectural rules
- `knowledge_source` remains the source-of-truth for provenance and trust.
- `crawl_source` only stores crawler execution configuration.
- fetched content is versioned by canonical URL + SHA-256 content hash.
- changed content creates a new `crawled_document`; older evidence is not overwritten.
- crawler output is never authoritative knowledge.
- extracted text creates a `knowledge_candidate` with origin `CRAWLER` and status `PENDING_REVIEW`.
- human review remains required before authoritative knowledge is changed.

## Pipeline
1. SourceRegistry
2. UrlDiscovery
3. PageFetcher
4. ContentExtractor
5. ChangeDetector
6. Candidate creation
7. Audit trail

## Storage
- `crawl_source`
- `crawl_job`
- `crawl_url`
- `crawled_document`
- existing `knowledge_candidate`
- existing `knowledge_source`

## Supported content in this foundation
- HTML: text/title/link extraction
- text/plain
- JSON
- XML
- PDF: download/hash/version detection and metadata only; full PDF text extraction is intentionally deferred to a dedicated document-extraction enhancement

## Safety and operational notes
- per-source host restriction
- max depth
- max page count
- timeout
- request delay
- user-agent
- duplicate URL suppression per job
- content change detection

## robots.txt
The schema includes `respectRobots`. Full robots.txt parsing/enforcement is not implemented in this foundation and must be completed before production-scale crawling of external sites. Until then, only sources where crawling is explicitly permitted should be configured.