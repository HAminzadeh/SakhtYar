# SakhtYar

پلتفرم هوشمند مشارکت در ساخت — Server-oriented Foundation.

این نسخه برای اجرا روی یک سرور لینوکسی اختصاصی طراحی شده، نه یک PC محدود.

## Target server

Baseline پیشنهادی:

- Ubuntu Server 24.04 LTS
- 8 vCPU
- 32 GB RAM
- 500 GB+ NVMe
- Docker Engine + Docker Compose
- Public domain + HTTPS

برای Local AI یا پردازش سنگین‌تر:
- 16 vCPU
- 64 GB RAM
- GPU در صورت نیاز

## Runtime topology

```text
Internet
   |
   v
Caddy / HTTPS
   |
   +--> React frontend
   |
   +--> Spring Boot API
           |
           +--> PostgreSQL + PostGIS + pgvector
           +--> Redis
           +--> MinIO
           +--> Worker processes
           +--> Agent runtime
           +--> Collector runtime
           +--> External integrations
```

## Architectural direction

SakhtYar در فاز اول یک Modular Monolith است، اما runtime آن server-oriented است:

- API process
- Background worker process
- Collector process
- Agent process
- PostgreSQL
- Redis
- MinIO
- Reverse proxy
- Observability

این جداسازی اجازه می‌دهد در آینده هر process را بدون بازنویسی domain جدا کنیم.

## Stack

### Backend
- Java 21
- Spring Boot 4.1.1
- Spring Web
- Spring Security
- Spring Data JPA
- Spring Validation
- Flyway
- PostgreSQL 17
- PostGIS
- pgvector
- Redis
- MinIO
- JWT cookie authentication

### Frontend
- React 19
- TypeScript
- Vite
- Material UI
- TanStack Query
- React Hook Form
- Zod
- RTL / Persian-first

### Server infrastructure
- Docker Compose
- Caddy
- PostgreSQL
- Redis
- MinIO
- Prometheus
- Grafana
- Node Exporter
- optional Ollama profile

## Services

```text
sakhtyar-proxy
sakhtyar-frontend
sakhtyar-api
sakhtyar-worker
sakhtyar-collector
sakhtyar-agent
sakhtyar-db
sakhtyar-redis
sakhtyar-minio
sakhtyar-prometheus
sakhtyar-grafana
```

در Phase 0 فقط API business featureهای اصلی را پیاده‌سازی می‌کند.
Worker/Collector/Agent processها foundation مستقل دارند تا در Phaseهای بعدی منطق روی آن‌ها قرار بگیرد.

## Production startup

```bash
cp .env.server.example .env
docker compose -f docker-compose.server.yml up -d --build
```

Observability:

```bash
docker compose -f docker-compose.server.yml --profile observability up -d
```

Local AI:

```bash
docker compose -f docker-compose.server.yml --profile ai up -d
```

## Development

برای development همچنان می‌توان از:

```bash
docker compose up -d db redis minio
```

استفاده کرد.

## Phase 0 functionality

- login/logout/current user
- bootstrap admin
- Case API
- document upload/download
- audit log
- PostGIS / pgvector
- Redis
- MinIO
- health endpoints
- React RTL shell
- CI
- production reverse proxy
- observability profile
- separate background runtime foundations

## Product reference

مرجع محصول همچنان «طراحی جامع مشارکت ساخت 02» است.
