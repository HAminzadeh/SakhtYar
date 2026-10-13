# Phase 5.17 - Public BFF contract

- `GET /api/web/v1/public/health` (pre-existing)
- `GET /api/web/v1/public/capabilities` (new)
- `GET /api/web/v1/public/market?city=...&district=...&days=30` (pre-existing)
  returns `{ generatedAt, summary, trend, snapshot }`.
- `GET /api/web/v1/market/comparables?city=...&district=...&limit=20` (new, `CASE_READ` required)
  returns `{ generatedAt, sampleCount, comparables, disclaimer }`.
- `GET /api/web/v1/cases/{caseId}/summary` (pre-existing, MUST be access-controlled).

`summary` uses `sampleCount, medianPricePerM2, averagePricePerM2, changePercent,
latestObservedAt, currencyCode, freshnessStatus`. Empty sampleCount is **no data**,
not a real estimated price. Never grant private case read access to a WordPress
service token. Authorized React users access cases through their own identity.