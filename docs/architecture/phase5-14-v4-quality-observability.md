# Phase 5.14 v4 — Quality, Observability, GeoNames & AI Playground

## GeoNames
The importer now:
- uses PostgreSQL `now()` for import timestamps
- avoids `Instant` binding ambiguity in JdbcTemplate
- logs structured `MASTER-DATA-IMPORT` events to the backend console
- exposes current stage and last error
- supports Quick (`cities500.zip`) and Full (`allCountries.zip`) city imports
- shows import history and failures in Settings

## HTTP latency
Local management configuration enables an HTTP server request histogram and SLO buckets for:
- P95 latency
- slow endpoint ranking

Histogram data appears after the backend is restarted and new HTTP traffic is generated.

## Tempo service graphs
Tempo metrics-generator now enables:
- `span-metrics`
- `service-graphs`

Generated metrics are remote-written to Prometheus. Prometheus is configured with:
`--web.enable-remote-write-receiver`.

Metrics are generated only for traces ingested after metrics-generator is enabled.

## Grafana
The Tempo datasource is linked to:
- Prometheus for service maps
- Loki for trace-to-log correlation

## AI Playground
`/api/v1/admin/ai/playground/run` uses the real `AiGateway`.
Each run can produce a real `ai_usage_event` and surfaces:
- provider
- model
- primary/fallback status
- latency
- input/output tokens
- raw response
- structured response

## Dashboard visual quality
The v4 UI adds:
- stronger information hierarchy
- sticky admin tabs on desktop
- richer KPI cards
- higher-quality chart/card surfaces
- clearer health/routing nodes
- improved tables and empty states
- dedicated GeoNames progress/history UI

No synthetic quality score or SLO data is fabricated.