# SakhtYar Phase 5.16 — Service-backed Intelligence Backend

This release is intentionally backend-only.

## Installed scope

- Agents delegate to normal application services through deterministic `AgentTool` adapters.
- Builder and contract domains expose real application services and REST APIs.
- Market Intelligence stores normalized observations and produces summaries, trends, comparables and publishable snapshots.
- Monitoring evaluates market rules and writes durable notification outbox records.
- Intelligence workflows receive a durable DAG executor.
- Website/WordPress can consume a dedicated `/api/web/v1` BFF instead of calling domain internals.
- No WordPress business logic is introduced.

## New modules

- `market-intelligence`
- `notification`
- `monitoring`
- `web-bff`

## Completed modules

- `builder`
- `contract`
- `agents`
- `intelligence`

## Agent rule

Agents interpret intent, identify missing inputs and explain results.
Business calculations and authoritative data access stay in backend application services.

## Market data rule

No price is fabricated.
Valuation requires persisted normalized observations.
Crawler adapters can feed `/api/v1/market/observations` after normalization and deduplication.

## Cloud/cache boundary

Cloudflare Tunnel, KV and R2 are deployment infrastructure and are intentionally not domain logic.
`/api/web/v1` and `market_snapshot` provide the stable publication boundary for that later stage.
