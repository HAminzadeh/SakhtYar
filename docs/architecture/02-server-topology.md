# Server Topology

## هدف

مبنای اجرا یک dedicated Linux server است.

Baseline:

```text
8 vCPU
32 GB RAM
NVMe
Ubuntu Server
```

پروژه طوری طراحی می‌شود که از منابع سرور استفاده کند و محدود به footprint یک PC نباشد.

## Process boundaries

یک codebase جاوا داریم، ولی چند runtime process:

```text
API
Worker
Collector
Agent
```

هر process profile مستقل Spring دارد.

این مدل مزیت Modular Monolith را حفظ می‌کند و در عین حال isolation عملیاتی می‌دهد.

## چرا این مدل؟

- API با scraping/AI block نمی‌شود.
- memory leak یک worker کل سیستم را از کار نمی‌اندازد.
- resource limit هر process مستقل می‌شود.
- بعداً هر runtime را می‌توان روی سرور دیگر منتقل کرد.
- deployment هنوز ساده‌تر از Kubernetes است.

## Infrastructure

### PostgreSQL

System of record.

Extensions:

- PostGIS
- pgvector
- pgcrypto

### Redis

کاربرد:

- cache
- distributed lock
- rate limiting
- async job coordination
- short-lived state

### MinIO

اسناد و فایل‌ها.

### Caddy

- TLS
- reverse proxy
- compression
- security headers

### Prometheus / Grafana

در profile جدا، ولی برای production توصیه می‌شود.

## Scaling path

مرحله 1:

```text
1 server
```

مرحله 2:

```text
server 1: proxy/api/frontend
server 2: worker/collector/agent
server 3: postgres
```

مرحله 3:

```text
managed database
object storage
multiple API nodes
multiple workers
```

در هیچ مرحله‌ای business domain نیاز به بازنویسی ندارد.
