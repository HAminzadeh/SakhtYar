# فاز ۰۱ / امنیت و حریم خصوصی / پایگاه‌داده مشترک

**Source reviewed:** `HAminzadeh/SakhtYar`, branch `v2`, commit `787cffc6755b384d92160826faf936b47cc5db9f` (2026-11-21 11:53:15 UTC)

## تصمیم معماری
- هیچ TEST database، clone، container و محیط زیرساختی جداگانه ایجاد نمی‌شود.
- REAL و TEST در **همان PostgreSQL موجود** نگهداری می‌شوند و باید در رکورد/Workspace، API و principal دیتابیس کنترل شوند.
- تغییرات این بسته migration، write روی دیتابیس، بازنشانی credential، ایجاد role یا فراخوانی HTTP/SMS/AI خارجی ندارند.
- نقش `TESTER` در سامانه با نقش اتصال PostgreSQL متفاوت است. یک session GUC مانند `app.sakhtyar_scope` قابل جعل توسط اتصال SQL است و به‌تنهایی مرز اعتماد محسوب نمی‌شود.

## ماتریس شواهد (Source audit)

| مسیر | قابلیت / شاهد کد | نتیجه / شکاف | اولویت | اقدام در این بسته | اثبات |
|---|---|---|---|---|---|
| `db/migration/V58__simulation_foundation.sql` | scope markers بر بسیاری از جداول؛ پیش‌فرض REAL | تنها metadata، نه isolation سراسری | P0 | حفظ؛ مستندسازی | DBA audit read-only |
| `db/migration/V60__core_test_scope_guard.sql` | FORCE RLS، policy و trigger بر ۴ جدول هسته | وابسته به DB role non-bypass و session context | P0 | حفظ و بررسی آمادگی | read-only catalog SQL؛ runtime BLOCKED |
| `db/migration/V66__simulation_fixture_ledger_test_scope.sql` | ledger TEST-only؛ حفاظت صف notification/crawler | حفاظت خروجی‌های دیگر اثبات نشده | P0 | حفظ | SQL catalogue + runtime BLOCKED |
| `quality/SimulationCoreFixtureService.java` | restricted PostgreSQL LOGIN جدا برای TEST workspace در همان DB | فقط چهار جدول؛ سایر workflowها کامل نیستند | P0 | reuse بدون provision خودکار | نیاز به DB proof در محیط اصلی |
| `quality/Phase08SafetyAdmission.java` | gate مسدودکننده قبل از business E2E write | خود کد `fullApplicationIsolationVerified=false` گزارش می‌کند | P0 | عدم دورزدن gate | تست‌های موجود؛ با DB اثبات شود |
| `casefile/CaseAccessService.java` | REAL-only و عضویت/مالکیت؛ TESTER ممنوع | تست منفی بیشتر لازم بود | P0 | تست منفی سه حالت | `Phase01CaseScopeSecurityTest` |
| `identity/config/SecurityConfig.java` | GET Quality => QUALITY_VIEW، POST /runs => QUALITY_RUN_SAFE | catch-all سابق، متدهای غیرمجاز دیگر را QUALITY_VIEW می‌داد | P1 | denyAll برای مابقی methods | کد و source audit، HTTP integration باقی است |
| `identity/security/JwtCookieAuthenticationFilter.java` | JWT session lock و اعتبارسنجی | path ارسالی کاربر در warn log | P1 | حذف path از warn log | static contract + build |
| `identity/security/SecurityDiagnosticsFilter.java` | trace با principal، authorities و fingerprint CSRF | افشای اطلاعات تشخیصی قابل پیوند | P1 | حداقل‌سازی log و DEBUG | `Phase01SecurityPrivacyRegressionTest` |
| `application.yml` و `application-production.yml` | برخی development secret defaults سراسری | production فاقد override required secret | P0 | اجبار env و Secure cookie در production | `Phase01ProductionSecurityContractTest` |
| `document/Phase03DocumentSafety.java` | محدودیت 25MiB، MIME/signature و object-key | اسکن بدافزار و اثرات ZIP داخل فایل اثبات نشده | P1 | بدون تضعیف؛ باقیمانده | security smoke سپس malware scan plan |
| `crawler/CrawlTargetPolicy.java`, `PageFetcher.java` | DNS destination/redirect validation و size bound | DNS rebinding با CIDR egress firewall نیز باید مهار شود | P1 | reuse؛ no external test | network gate لازم |
| Auth session persistence | ذخیره hash refresh token، lifetime capped | expiry cleanup/PII purge policy کامل نیست | P1 | تصمیم/کنترل مستند؛ بدون حذف REAL | retention policy approval required |

## وابستگی‌ها
`JWT/session -> SecurityFilterChain/RBAC -> CaseAccessService -> Case/Owner/Document/Quality API`

`TEST workspace -> restricted PostgreSQL login -> RLS + scope trigger (V60) -> test fixture (4 core tables)`

`test effect -> queue trigger (V61/V66) -> external egress adapters -> network enforcement (unverified)`

## Threat model و محدودیت‌های باز
- **IDOR / cross-workspace:** `CaseAccessService` دامنه REAL را کنترل می‌کند؛ endpointهای صرفاً مبتنی بر Document ID و سایر دامنه‌ها نیاز به تست مالکیت مستقل و HTTP واقعی دارند.
- **RLS:** وجود ستون `data_scope` در V58 به معنی وجود سیاست RLS نیست. برای domainهای بیرون از چهار جدول هسته، isolation کامل تأیید نشده و تست نوشتاری business باید fail-closed بماند.
- **DB principal:** استفاده از runtime DB role با superuser، BYPASSRLS، owner privilege یا capability فراتر از نیاز نباید مبنای تأیید E2E قرار بگیرد. صرف `SET ROLE` یا GUC گواه principal مستقل نیست.
- **CSRF:** از تنظیمات موجود SPA و عدم استثنای refresh وب محافظت می‌شود؛ negative HTTP test همچنان نیاز است.
- **CORS:** allowed-origins exact فعلی reuse می‌شود؛ در production باید `CORS_ALLOWED_ORIGINS` صریح و HTTPS باشد.
- **SQL injection:** APIها و Repositoryها باید با query parameterization و code review کامل بررسی شوند؛ این بسته ادعای ممیزی کامل همه moduleها ندارد.
- **Secrets:** اسکن repo شامل history، CI secrets، Docker manifests و dependency CVEs باید در محیط مجهز جداگانه (فقط بررسی کد، نه جداکردن TEST DB) اجرا شود. وجود default development secret در `application.yml` ریسک باقی است؛ production با env required رفع می‌شود.
- **File upload:** MIME/magic نخستین کنترل است؛ AV/CDR و recursive ZIP-bomb defense مستقلاً مورد نیاز است.
- **SSRF:** مقصدهای واقعی crawler باید علاوه بر code checks در network egress firewall محدود شوند.
- **PII / Retention:** لاگ جدید عمداً principal و cookie را حذف می‌کند. حذف داده کاربران و retention auth sessions بدون مبنای قانونی، مصوبه retention و مسیر حذف کنترل‌شده پیاده نشد. هیچ داده REAL حذف نمی‌شود.
- **Frontend:** کنترل کلاینت برای مجوز کافی نیست؛ مرجع امنیت Server و DB است. تست RTL/UX فاز مربوطه مستقل انجام شود.

## اقدامات گیت پذیرش
1. HEAD و status لوکال بررسی شده و با baseline مطابقت داشته باشد؛ تغییرات ناشناخته از بین نروند.
2. `Audit`, `DryRun`, `ApplySource` بدون مجوز DB/egress و با backup و rollback موفق باشند.
3. `Verify` حداقل source contracts و Maven unit tests را اجرا کند؛ نتیجه `PASSED` صرفاً پس از اجرای واقعی گزارش شود.
4. با principal محدود واقعی همان پایگاه‌داده، negative SELECT/UPDATE/DELETE بر REAL و cross-workspace TEST اثبات شود.
5. HTTP 401/403/404، bearer/cookie/CSRF و HEAD/POST/PATCH/delete در APIها تست شوند.
6. external side effects و token/PII leakage و retention با اسناد و شواهد عملی تأیید شوند.
7. تا تکمیل گیت‌های ۴ تا ۶، **نتیجه کل فاز: READY_TO_VERIFY / NOT FULLY ACCEPTED** و **Business E2E writes: BLOCKED**.

## جلوگیری از آثار جانبی
- ZIP فقط سورس و تست و read-only SQL و docs را تغییر می‌دهد؛ هیچ Migration جدید (V67) اضافه نشده است.
- بخش SQL صرفاً catalog metadata را می‌خواند؛ هیچ جدول داده REAL/TEST را SELECT محتوایی نمی‌کند.
- ویژگی‌های `quality` و `development` قبلی بدون تغییر می‌مانند؛ این معماری به معنی ایجاد database جدا نیست.

## نگهداشت داده و redaction
- پیشنهاد قابل بررسی: نگهداری metadata امنیتی با حداقل فیلد؛ عدم ذخیره raw JWT, refresh token، username در trace عمومی؛ دسترسی محدود به logها؛ گردش secret با برنامه مصوب.
- retention و right-to-erasure باید پس از بررسی الزام قانونی/قراردادی، ابلاغ دوره‌های مجاز و تست حذف scoped با Backup رسمی اعمال شود، نه به‌صورت خودکار از patch امنیتی.

## Rollback
با `-Mode Rollback -BackupDir <مسیر backup>` بعد از اعمال patch، فقط فایل‌هایی که هششان همان خروجی patch است برگردانده می‌شوند. تغییرات بعدی کاربر موجب توقف محافظه‌کارانه می‌شود. این مکانیزم `git reset`, `git clean`, `drop`, `truncate` انجام نمی‌دهد.

## Handoff
ادامه: تکمیل RLS/ownership در دامنه‌های خارج از core، API negative auth و DB principal proofs، retention policy و dependency scanning. Baseline `787cffc6755b384d92160826faf936b47cc5db9f`, last Flyway `V66`, new migration `NONE`. تست‌های Runtime/DB/Browser در این محیط انجام نشده‌اند.

## Offline source inventory

`python scripts/security/phase01_source_security_inventory.py --root <repository>` فقط مسیر و شماره خط و دسته هشدار را چاپ می‌کند؛ هیچ متن match یا مقدار Secret را افشا نمی‌کند. این اسکن جایگزین secret scanning تاریخچه Git یا بررسی CVE روز نیست.
