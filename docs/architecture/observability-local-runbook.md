# Local Observability Runbook

Copy `.env.observability.local.example` to `.env.observability.local`.

Validate:
```powershell
docker compose --env-file .env.observability.local `
  -f docker-compose.server.yml `
  -f docker-compose.observability.local.yml `
  --profile observability config
```

Start:
```powershell
docker compose --env-file .env.observability.local `
  -f docker-compose.server.yml `
  -f docker-compose.observability.local.yml `
  --profile observability up -d db redis minio api prometheus loki tempo alloy grafana
```

Check API at `http://localhost:8080/actuator/health`, Prometheus at port 9090,
Grafana at port 3001, Loki readiness at port 3100 `/ready`, and Tempo at port 3200 `/ready`.

These credentials are development-only.
