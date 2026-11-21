# فاز ۰۲ — Database / Flyway / RLS / Integrity (ساخت‌یار)

## مبنا و مرز اجرایی

- GitHub `HAminzadeh/SakhtYar`, branch `v2`, baseline `da95d441c0a2325e0ea0cbad1d5b1540123277cd` (2026-11-21 12:12:09 UTC).
- آخرین نسخه موجود Flyway در زمان بررسی: **V66**؛ همه V1..V66 از GitHub خوانده و فایل‌ها/هش‌های committed در baseline inventory ثبت شده‌اند.
- **یک PostgreSQL مشترک** برای `REAL` و `TEST`؛ هیچ دیتابیس، سرویس، bucket یا environment جداگانه‌ای با این بسته ایجاد نمی‌شود.
- **این بسته Flyway V67 اضافه نمی‌کند**: افزودن RLS سراسری در چهارچوب shared DB بدون شواهد role/grants، query compatibility و verification عملی می‌تواند سبب outage یا نشت شود.
- هیچ SQL روی دیتابیس به‌صورت خودکار اجرا نمی‌شود. دستورهای SQL ارائه‌شده فقط `BEGIN READ ONLY`, `SELECT`, `SET LOCAL`, `ROLLBACK` دارند.

## تحلیل شکاف بر اساس سورس

| مسیر | قابلیت موجود / شاهد | شکاف یا محدودیت تأییدشده | اولویت | تصمیم | اثبات |
|---|---|---|---|---|---|
| `V58__simulation_foundation.sql` | markerهای `data_scope`, `test_workspace_id` در حدود ۶۰ جدول | افزودن marker الزاماً تمام repository/query/background را ایزوله نمی‌کند | P0 | reuse؛ ممیزی دامنه | read-only pg_catalog + تست‌های منفی |
| `V60__core_test_scope_guard.sql` | FORCE RLS و trigger روی چهار جدول هسته | خارج از چهار جدول پوشش عمومی اثبات نشده | P0 | preserve؛ تحلیل پوشش | catalog + workspace negative |
| `V61`, `V66` | trigger منع TEST در `crawl_job` و `notification_outbox` | سایر خروجی‌های شبکه، AI، MinIO و dispatch نیازمند کنترل جدا هستند | P0 | reuse؛ گزارش ساختاری | fake adapters و network egress test |
| `V65`, `V66` | دفتر ledger با TEST tag و trigger | دفتر ledger، مجوز write سراسری ایجاد نمی‌کند | P1 | preserve و inventory | migration integrity + catalog |
| `T10RuntimeRoleGuard` | enforce opt-in DB role restriction | روی همه profileها بدون بررسی فعال نیست | P0 | وضعیت runtime فقط‌خواندنی | نقش واقعی PostgreSQL |
| `Phase02RestrictedIsolationProbe` | اثبات محدود چهار جدول با principal فیزیکی TEST | هویت SQL عمومی/`set_config` جایگزین login restricted نیست | P0 | reuse؛ محدود به CORE | مشاهده دو Workspace مستقل |
| `SecurityConfig` | کیفیت برای QUALITY_VIEW، سایر عملیات کنترل شده | catalog امنیتی جدید باید USER_MANAGE را مستقل بررسی کند | P1 | controller با deny-before-query | Mock MVC/controller tests |
| `application-production.yml` | Flyway خاموش؛ اسرار explicit و cookie امن | deploy migration باید operator-approved باقی بماند | P0 | بدون تغییر | source audit |
| `scripts/validate_migrations.py` | بررسی نسخه‌های پیوسته و duplicate | محافظت از ۶۶ نسخه committed و drift محلی مستقل نیست | P1 | ابزار جدید هشِ Git و نسخه | unit/offline test |

## طبقه‌بندی ابتدایی جداول

1. **CASE / DOMAIN SCOPED**: `construction_case`, `property`, `property_owner`, `case_document`؛ دامنه‌های پرونده‌محور مانند contract/finance/analysis/estimate نیازمند lineage guard جدا هستند.
2. **TEST-WORKSPACE METADATA**: `simulation_workspace`, `simulation_run`, `simulation_result`, `simulation_e2e_*`, `simulation_fixture_contract_ledger`, `t10_e2e_*`. بیشتر اینها ledger/diagnostic هستند نه داده تجاری.
3. **SHARED / GLOBAL / IDENTITY**: `global_*`, `app_user`, `auth_session`, platform configuration و catalogهای مرجع. باز/بسته‌کردن RLS روی این جداول باید بر اساس مسیر واقعی caller انجام شود، نه صرف نام.
4. **REVIEW REQUIRED**: سایر جداول `data_scope`‌دار در V58؛ داشتن marker به‌تنهایی به معنای tenant protection یا RLS نیست.

این طبقه‌بندی `case-scoped` را معادل `tenant-scoped` قطعی فرض نمی‌کند؛ تعیین مالک هر جدول باید با FK و cross-domain access بررسی شود. فایل SQL فهرست واقعی در runtime را از `pg_catalog` گزارش می‌دهد.

## Threat model / safety boundaries

- `TESTER` در برنامه، PostgreSQL role نیست و داشتن `QUALITY_VIEW` مجوز خواندن catalog امنیتی نیست.
- session variables از نوع `app.sakhtyar_scope` قابل تنظیم هستند و **به‌تنهایی مرز احراز هویت دیتابیس نیستند**.
- superuser و `BYPASSRLS` از RLS عبور می‌کنند؛ FORCE RLS فقط owner عادی را شامل می‌شود؛ بنابراین login واقعی باید جداگانه بررسی شود (بدون ساخت دیتابیس جدید).
- `V60` برای CORE داده REAL را پیش‌فرض حفظ می‌کند؛ تغییر migrationهای اجراشده ممنوع است.
- جزئیات SQL خطادار، Secret، داده شخصی و URL اتصال DB از endpoint مدیریت بازگردانده نمی‌شوند.
- این بسته `fullApplicationIsolationVerified` یا `businessE2eWritesAuthorized` را **هرگز true اعلام نمی‌کند**.

## Endpoint جدید (بدون تغییر API قبلی)

`GET /api/v1/admin/quality/database-isolation`، فقط برای کاربر احراز هویت شده دارای `USER_MANAGE`، و صریحاً نه `ROLE_TESTER`. در Backend از `pg_catalog` صرفاً جدول‌ها، policy/trigger و خصوصیات **غیرمحرمانه** principal خوانده می‌شود؛ نه داده‌های REAL، نه TEST، نه رمزها. خروجی `OBSERVATION_ONLY` است، نه جواز نوشتن TEST. دریافت خطای DB ⇒ HTTP 503 بدون افشای جزئیات اتصال.

## گام‌های اجرایی با یک دیتابیس مشترک

1. `Audit` و `DryRun` Installer را اجرا کن. بقیه تغییرات محلی حفظ می‌شود.
2. `ApplySource` فقط فایل‌های جدید سورس/تست/اسکریپت/مستندات را اضافه می‌کند. نه V67، نه DDL، نه SQL execution.
3. `Verify` بررسی Git blob پایه V1..V66، نصب فایل‌ها، آزمون‌های Python و تست JUnit هدفمند را اجرا می‌کند.
4. SQL ممیزی را **فقط با connection محدود و صرفاً خواندنی** اجرا کن؛ نتایج را بدون Secret ارسال کن.
5. برای negative proof واقعی، با همان دیتابیس اصلی اما دو Workspace TEST مجاز و دو physical restricted login اثبات کن: عدم دسترسی Test A به Test B/REAL؛ قبل از هر تستِ write نیاز به اثبات مستقل و کنترل side-effect است.
6. بررسی `pg_dump`، integrity checksum، برنامه restore باید پیش از هر schema change توسط operator تصویب شود. **Restore روی دیتابیس اصلی ممنوع است**؛ طبق تصمیم پروژه هیچ clone/test environment جدیدی خودکار ایجاد نمی‌شود، بنابراین restore drill اکنون `BLOCKED` است.

## Backward compatibility / rollback

- هیچ REST endpoint موجود، DTO موجود، migration قدیمی، schema یا داده تجاری تغییر نمی‌کند.
- source rollback فقط فایل‌های جدید را در صورتی که hash آنها عین payload باشد و بعداً به Git commit نشده باشند حذف می‌کند؛ فایل‌های دیگر را هرگز دستکاری نمی‌کند.
- اگر HEAD محلی از baseline جلوتر باشد، installer برخلاف فاز ۰۱ **ancestor check** دارد؛ روی تغییر غیرمنتظره یکی از فایل‌های هدف یا drift migrationهای تاریخی fail closed می‌شود.

## Acceptance gate

| بررسی | Gate اولیه |
|---|---|
| Source audit / inventory / SHA baseline | READY_TO_VERIFY روی Windows |
| Unit و offline contract | قابل اجرا بدون DB |
| Runtime pg_catalog / GRANT / FK / RLS | BLOCKED تا اجرا روی DB اصلی با connection readonly |
| Negative cross-workspace and REAL invisibility | BLOCKED تا احراز هویت واقعی TEST و baseline مستقل |
| Query-plan و latency | BLOCKED تا مشاهده runtime |
| Flyway apply/validate | BLOCKED (نیازمند فرآیند migration تأییدشده) |
| Restore drill | BLOCKED (هیچ محیط کلون خودکار ساخته نمی‌شود) |
| Global TEST/REAL isolation | NOT PROVEN؛ TEST business writes همچنان fail-closed |

این گزارش **آزمون PostgreSQL انجام‌شده نیست**. نقطه‌های مسدود با برچسب موفق ارائه نمی‌شوند.
