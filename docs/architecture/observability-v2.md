# SakhtYar V2 Observability

Application logs, metrics, distributed traces, audit logs, agent traces, and evidence are distinct concerns.

- Structured application logs -> Loki
- Metrics -> Micrometer/Prometheus
- Distributed traces -> OpenTelemetry/OTLP -> Tempo
- Dashboards -> Grafana
- Audit and evidence remain business data, not generic logs.

HTTP requests use `X-Correlation-ID`. Safe incoming IDs are preserved; otherwise a UUID is generated. The ID is returned in the response and placed in MDC as `correlationId`.

Never deliberately log passwords, JWT/refresh tokens, cookies, Authorization headers, API keys, document bodies, or raw AI prompts. Request-body logging is intentionally not enabled.
