# Phase 5.14 — AI Control Plane & Operations Center

## Block 1 — Core AI Control Plane
Provider Registry, Model Registry, AI Gateway, Agent routing, primary/fallback/canary, prompt versioning and usage events.

The current executable runtime adapter remains Ollama. Additional provider records can be managed immediately; real execution requires a matching runtime adapter.

## Block 2 — Operations & Observability
SakhtYar does not embed Grafana/Loki/Tempo/Prometheus consoles.
The Operations API summarizes Prometheus metrics, Loki/Tempo readiness and service health, then exposes deep links for technical investigation.

## Block 3 — Reliability, Governance & Safety
Schema and APIs support canary, routing strategies, budgets, rate-limit fields, guardrails, local-only policy, cost/latency ceilings and separate AI/Operations permissions.

## Block 4 — UI + Contextual Help
Pages:
- `/admin/ai`
- `/admin/operations`

Every new admin area includes contextual Help explaining purpose, usage, safe workflow and risks.

## Validation
Restart backend so Flyway V30 applies, restart Vite, login as ADMIN, test Ollama provider, verify PERSIAN route, run one assistant request and confirm AI Usage increments, then verify Operations service cards.