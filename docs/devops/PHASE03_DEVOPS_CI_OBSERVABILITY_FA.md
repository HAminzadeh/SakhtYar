# فاز ۰۳/۴۰ — Docker، CI/CD، Observability و استقرار

**بررسی GitHub:** شاخه `v2`، Commit مبنا `43e987ab44c6952cfd891f9beb12cdd547e4c6ba` (2026-11-21 12:46:13 UTC).
**دامنه:** بهبود زیرساخت با تغییر سورس؛ نصب‌کننده دیتابیس، سرویس‌ها یا نقش‌ها را تغییر نمی‌دهد. **وضعیت: READY_TO_VERIFY / RUNTIME_BLOCKED**.

## معماری مورد توافق
- توسعه UI در IntelliJ و پروفایل‌های `development`/`production` روی **همان PostgreSQL موجود** است.
- TEST و REAL با `data_scope`، `test_workspace_id`، RLS و اتصال محدود واقعی تفکیک می‌شوند؛ پروفایل به‌تنهایی مرز امنیتی نیست.
- PostgreSQL `POSTGRES_USER` صرفاً برای بوت‌استرپ/عملیات DBA، `SAKHTYAR_RUNTIME_DB_USER` حساب مستقل و از پیش ایجادشده و بدون `SUPERUSER`/`BYPASSRLS` است.
- CI فعلی یک PostgreSQL **موقتی و دورریختنی صرفاً برای آزمایش migration** ایجاد می‌کند؛ هیچ داده واقعی یا دیتابیس کاربردی TEST دوم در مسیر اجرای محصول ایجاد نمی‌شود. اجرای آن برابر اثبات RLS روی دیتابیس اصلی نیست.
- استقرار production پیش از تأمین نقش محدود، فایل secrets و backup مستقل به‌صورت پیش‌فرض متوقف می‌شود.

## ممیزی و شواهد
| مسیر واقعی | قابلیت پیشین | شکاف مشاهده‌شده | تغییر/تصمیم | آزمون |
|---|---|---|---|---|
| `docker-compose.yml` | سرویس‌های API/worker/collector/agent و DB | کاربرد `POSTGRES_USER` برای JVM؛ عدم فعال‌سازی Production؛ env رمز Redis در command | نقش مستقل، `production`، ممنوعیت Flyway/seed، Healthcheck آماده‌بودن API و بستن exporter host-mode به پروفایل دستی | offline guard + `docker compose config` |
| `backend/app/.../application-production.yml` | secrets الزامی و Flyway خاموش | `DB_URL` در application.yml استفاده نشده و احتمال اتصال به `localhost`؛ سامپلینگ ۱۰۰٪ | تعریف datasource URL و role guard، نمونه‌برداری trace ۵٪ | source gate + Maven |
| `deploy/caddy/Caddyfile` | TLS reverse proxy | انتشار `/actuator/*` از اینترنت | فقط `/api/*` در مسیر API عمومی؛ Actuator صرفاً داخلی | source gate + smoke محافظت‌شده |
| `scripts/deploy-server.sh` | Deploy | اشاره به `docker-compose.server.yml` ناموجود | reference صحیح و preflight/تأیید دستی/backup proof و `docker compose up --wait` | shell syntax/manual deployment review |
| `scripts/backup-server.sh` | pg_dump | `source .env` و نبود بررسی dump و SHA256 | دریافت env از container، dump اتمیک، `pg_restore --list`، sha256 | syntax + restore drill مستقل |
| `compose.spring-local.yml`, `docker-compose.observability.local.yml` | پورت‌های local | binding همه اینترفیس‌ها | اتصال به `127.0.0.1` | Compose config |
| `.env.server.example` | راهنمای secrets | عدم تعریف runtime login مستقل و نام منقضی JWT expiration | افزودن keys جدید و منع placeholder در preflight | Python tests |
| `.env.observability.local.example` | راهنمای dev | `APP_DEV_SEED_ENABLED=true` | false مطابق عدم درج خودکار داده‌های تست | contract |
| `.github/workflows/ci.yml` | Maven 21، npm24 و Flyway smoke | فقط V45/V46 کنترل می‌شد؛ مدرک DevOps نبود | کنترل V66/تعداد ۶۶ migration، ممیزی امنیتی و artifact | GitHub Actions بعد از Push |
| `deploy/observability/alloy.alloy` | انتقال log به Loki | ورود عمومی لاگ‌ها بدون stage پاک‌سازی | پردازش `stage.replace` و محدودسازی سرویس‌ها | Alloy validate/CI محیط مناسب، pending |
| `deploy/observability/loki.yml`, `tempo.yml` | ذخیره داده | محدودیت retention صریح نبود | retention ۷ روزه با Loki compactor و Tempo compactor | Docker real config/retention drill |
| `deploy/prometheus/prometheus.yml` | scraping API و exporter | `host.docker.internal:9100` برای Linux/host-network اثبات نشده | موقتاً حذف scrape نامعتبر node؛ node-exporter مستقل فعال شود | `promtool check config` در محیط مقصد |
| `frontend/nginx.conf` | مسیر `/api` | `backend:8080` سرویسی به این نام در compose اصلی نیست | `api:8080` | nginx -t بعد از build |
| `deploy/postgres/Dockerfile` | PostgreSQL17+PostGIS+pgvector | pin digest و supply-chain verification هنوز اثبات نشده | تغییر خودکار نکردیم، audit blocker | review image digest |
| `deploy/minio-local/Dockerfile` | Build موقتی MinIO | تغییر version/digest بدون بررسی ممنوع | بدون تغییر؛ ثبت risk | review image provenance |

## Dependency graph
`GitHub CI → {Maven 21, npm24, Offline Guard, Flyway disposable smoke}`
`Caddy → frontend / API → restricted PostgreSQL LOGIN → same primary PostgreSQL {REAL/TEST}`
`API → Prometheus {metrics} + Tempo {tracing} + Alloy → Loki {logs} → Grafana`
`Backup operator → pg_dump → verify archive → off-repository backup; restore drill on independent approved target only`

## حساب محدود Runtime، بدون خلق خودکار Role
1. مدیر دیتابیس با یک روش تأییدشده و بررسی تمام grant ها، حساب PostgreSQL محدود و غیر superuser و فاقد BYPASSRLS ایجاد کند. **هیچ دستور SQL به‌صورت خودکار اجرا نمی‌شود.**
2. در سرور `.env` واقعی را خارج از Git نگه دارید و `SAKHTYAR_RUNTIME_DB_USER/PASSWORD` را مقداردهی کنید؛ نباید با `POSTGRES_USER/PASSWORD` برابر باشند.
3. نقش را روی همان پایگاه داده بررسی کنید؛ `T10RuntimeRoleGuard` فقط نبود `SUPERUSER/BYPASSRLS` را در startup کنترل می‌کند. نبودن این دو به‌تنهایی اثبات least privilege، tenant isolation، عضویت Role و دسترسی به REAL نیست.
4. `scripts/database/phase02_catalog_readonly.sql` و `phase02_isolation_gate_readonly.sql` را با **اتصال محدود واقعی** در حالت read-only اجرا و نتایج را بررسی کنید. Cross-workspace denial و عدم نشت REAL هنوز Gate جداگانه هستند.

## CI و تست‌ها
`python scripts/devops/phase03_infra_contracts.py --root . --report .phase03-reports/infra-source-gate.json`
`python -m unittest discover -s tests/devops -p test_phase03_infra_contracts.py -v`
`docker compose -f docker-compose.yml config --quiet` تنها با env تست *مصنوعی* در shell.
`cd backend && mvn -o -pl app -am test`
`cd frontend && npm run build`

**CI**: جریان فعلی Maven/npm مستقل حفظ می‌شود؛ CI فقط ساخت و contract تست‌ها را اثبات می‌کند؛ تست E2E تجاری در DB واقعی نه انجام و نه مجاز می‌شود. GitHub Actions پس از Push بررسی خواهد شد.

## دستور ایمن اجرای server preflight و backup (به‌صورت دستی و خارج از نصب‌کننده)
```
python3 scripts/devops/phase03_deploy_preflight.py --env-file .env
python3 scripts/devops/phase03_infra_contracts.py --root .
docker compose --env-file .env -f docker-compose.yml config --quiet
bash scripts/backup-server.sh
```
فایل `.env` باید خارج از Git و دارای secrets منحصر به فرد باشد. اسکریپت Backup فقط `pg_dump` می‌گیرد و **جایگزین backup کامل MinIO، Redis و Restore Drill نیست**. خروجی `sha256sum` و آرشیو غیر خالی تنها صحت حداقلی dump را ثابت می‌کنند، نه بازیابی موفق.

پس از بررسی انسانی و تهیه backup خارج از Repo، استقرار **دستی** با `SAKHTYAR_DEPLOY_CONFIRM=YES_DEPLOY` و در صورت وجود DB با `SAKHTYAR_BACKUP_PROOF` به دستور `bash scripts/deploy-server.sh` سپرده می‌شود. اسکریپت Docker را روی سیستم ChatGPT اجرا نمی‌کند. هیچ `docker compose down -v`, `prune`, `reset`, `flyway migrate` یا دستور مخرب اضافه نشده است.

## Smoke / Recovery / Rollback
- **Preflight**: بررسی `.env` بدون چاپ مقدار secret، بررسی `docker compose config`, `docker image inspect`, نبود role admin در JVM.
- **Internal smoke**: `docker compose ... exec -T api ...` یا درخواست `/actuator/health` فقط از شبکه داخلی Docker؛ بررسی API auth و test/real negative boundaries با اجازه جداگانه. عدم دسترسی public به `/actuator/prometheus` باید تأیید شود.
- **Backup verification**: آرشیو PostgreSQL + hash؛ تست بازیابی روی مقصد تأییدشده مستقل توسط اپراتور و نه روی DB اصلی. MinIO، Redis و تمام volume ها همچنان نیازمند Runbook مخصوص هستند.
- **Rollback source**: PowerShell `-Mode Rollback -BackupDir <backup created by ApplySource>`؛ اگر فایل تغییر کرده یا commit شده باشد، fail closed.
- **Rollback runtime**: صرفاً با نسخه image ثبت‌شده و روش اپراتور؛ هیچ blue-green واقعی در زیرساخت فعلی اثبات نشده است و **ادعای blue-green آماده** نمی‌کنیم. بازگشت migration/schema خودکار ممنوع است.

## Threats and residual blockers
- عرضه تصاویر Docker با تگ `latest` / عدم pin digest — P1: امکان تغییر بدون هش.
- استفاده از Docker socket توسط Alloy هرچند read-only bind — P1: دسترسی پرقدرت به متادیتای Docker؛ راهکار socket-proxy محدود باید ارزیابی شود.
- Regex redaction برای همه شکل‌های PII کافی نیست؛ stdout proxy، اپلیکیشن، exceptions یا span attributes هنوز می‌توانند حاوی PII باشند — P0: source-level safe logging و negative tests لازم است.
- retention در Loki نیاز به تست startup و تست deletion واقعی دارد — P1.
- Tempo/Prometheus internals نباید روی اینترنت منتشر شوند؛ local فقط loopback، production internal networks، اما firewall و ACL واقعی تأیید نشده‌اند — P0.
- `T10RuntimeRoleGuard` اثبات تمام grants/RLS نیست و تست REAL/TEST نیازمند login واقعی و negative probes است — P0.
- backup/restore MinIO، Redis، Object storage، Docker volume recovery و blue-green staging هنوز آزمون واقعی ندارند — P0.
- `node-exporter` host-network تا تأیید جداگانه فعال نمی‌شود — P1.
- Docker Compose config/Alloy validation، HTTP smoke و alert tuning در این محیط بدون Docker/DB اجرا نشده‌اند — BLOCKED.

## Acceptance gate
- **Source security contracts**: پس از نصب و `Verify` قابل بررسی.
- **Maven + frontend build**: پس از اجرای Windows برچسب PASSED فقط با خروجی واقعی.
- **Docker Compose / Alloy/Loki syntax**: باید در زیرساخت مقصد verify شود.
- **Live runtime role, RLS, REAL/TEST isolation**: PENDING. **Business E2E PASS** تحت هیچ شرایطی از این بسته نتیجه نمی‌شود.
- **Deployment/backup/restore drill**: NOT RUN.
