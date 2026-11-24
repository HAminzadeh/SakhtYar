# ساخت‌یار — مدیریت چرخه عمر Migration

مبنای سورس: شاخه `v2`، Commit `698280f3683979691f4e1a72fb7ab514186162ed`، 2026-11-24. تاریخچه گزارش‌شده: V1 تا V67 (آخرین ۱۵ رکورد موفق دیده شد)، V68 موجود در سورس و هنوز اجرا نشده. این ابزار یک فرایند **اپراتوری محلی** است؛ نه سرویس اجرای خودکار DDL و نه مجوز استقرار Production.

## پیش‌نیازها

Windows PowerShell 5.1، Python 3.9+، Git، Docker Desktop با context محلی و کانتینر `sakhtyar-local-db-1` (Compose project `sakhtyar-local`, service `db`). همان Image ID PostgreSQL برای Clone استفاده می‌شود. Flyway CLI به نسخه پین‌شده موجود CI (`flyway/flyway:11.15.0`) محدود است؛ Spring Boot کتابخانه دیگری دارد اما startup migration خاموش می‌ماند. در صورت نبود Image، با تأیید اپراتور `docker pull flyway/flyway:11.15.0` را جداگانه اجرا کنید. اسکریپت هرگز خودش Image دانلود نمی‌کند. اگر اعتبار کاربر Migration با POSTGRES_USER/PASSWORD کانتینر متفاوت است، `SAKHTYAR_FLYWAY_USER` و `SAKHTYAR_FLYWAY_PASSWORD` از secret store محلی تنظیم شوند؛ در متن دستور یا Git ننویسید.

مسیر گزارش و Backup پیش‌فرض: `D:\ChatGPT_Projects\SakhtYar\DatabaseBackups\MigrationLifecycle\<run-id>`؛ فایل dump و SQL نقش‌ها (بدون password hash) حاوی داده/metadata محرمانه هستند؛ ACL پوشه در Windows باید توسط اپراتور محدود شود. آن‌ها را upload، commit یا share نکنید. Backup از DB `sakhtyar` است، نه snapshot کامل cluster یا MinIO؛ برای Disaster Recovery جامع، backup سایر سرویس‌ها و اسرار جداگانه لازم است.

## فرمان‌ها

از ریشه مخزن، در PowerShell:

```powershell
$m = '.\scripts\database\Invoke-SakhtYar-DatabaseMigration.ps1'
powershell -NoProfile -ExecutionPolicy Bypass -File $m -Mode SelfTest
powershell -NoProfile -ExecutionPolicy Bypass -File $m -Mode Audit
powershell -NoProfile -ExecutionPolicy Bypass -File $m -Mode Plan
```

Audit تاریخچه و نام فایل و پیش‌نیازهای خاص V68 را می‌بیند؛ Plan علاوه بر آن Flyway validate را اجرا می‌کند (Pending مجاز، future/failed/drift مجاز نیست). هر دو روی DB اصلی فقط خواندنی هستند. وضعیت `PASS` در Audit یعنی اجرای ممیزی موفق، نه اینکه DB آماده راه‌اندازی است. نسخه‌های Pending در `gates.pending_versions` گزارش می‌شوند.

پس از تشخیص login واقعی برنامه (نه migration owner)، `-RuntimeRole` را دقیق تنظیم کنید. پیش‌فرض فقط متغیر `SAKHTYAR_RUNTIME_DB_USER` یا `SPRING_DATASOURCE_USERNAME` است. این گزینه به برنامه نقش تحمیل نمی‌کند: باید با secret/config واقعی IntelliJ یکسان باشد. اگر فعلاً برنامه با `sakhtyar` فوق‌مجاز متصل می‌شود، gate متوقف خواهد شد. هیچ role یا grant در دیتابیس اصلی خودکار تغییر نمی‌کند. الگوی DBA موجود: `docs/simulation/T08_RESTRICTED_ROLE_TEMPLATE.sql`، **صرفاً راهنمای بازبینی**. نقش محدود باید SELECT روی history داشته باشد اما INSERT/UPDATE/DELETE/TRUNCATE نداشته باشد، نباید owner، عضو role فوق‌مجاز یا دارای CREATE روی schema باشد. مجوزهای جدول‌های جدید باید در Clone از طریق default privileges موجود یا یک تغییر DBA جداگانه و بازبینی‌شده اثبات شود. برای V68، Policy شامل SELECT/INSERT/UPDATE مناسب و منع TRUNCATE، RLS/FORCE، triggerها و policyهای مورد انتظار است. اعمال خودکار GRANT سراسری یا تضعیف RLS ممنوع است. اگر grantهای موجود کافی نیستند، DryRun گزارش BLOCKED می‌دهد؛ مشکل را روی Clone و فرآیند DBA حل کنید، نه با bypass.

```powershell
# فقط اگر این نام واقعاً login محدود تنظیم شده برای Backend است:
$runtime = 'sakhtyar_runtime'
powershell -NoProfile -ExecutionPolicy Bypass -File $m -Mode Audit -RuntimeRole $runtime
powershell -NoProfile -ExecutionPolicy Bypass -File $m -Mode DryRun -RuntimeRole $runtime
```

حالت Backup آرشیو PostgreSQL و role metadata را می‌گیرد و لیست آرشیو/هش را بررسی می‌کند؛ حالت Clone از Backup تازه، roleها و DB را در کانتینر بدون پورت/شبکه بیرونی و volume جدید restore می‌کند و schema fingerprint را مقایسه می‌کند. DryRun علاوه بر این، Flyway migrate روی Clone، validate، history، بررسی مجوز واقعی و login فیزیکی محدود، یک تست منفی INSERT روی Clone، و حذف کانتینر/volume anonymous همان Clone را انجام می‌دهد. هیچ برنامه، MinIO، notification، AI یا worker روی Clone اجرا نمی‌شود. Clone داده واقعی کپی‌شده دارد و داده عمومی نیست. تست READ بدون ردیف ممکن است vacuous باشد؛ تست منفی V68 و catalog آن را تکمیل می‌کند، اما کل E2E و تمام قیود کسب‌وکار اثبات نمی‌شود. Role password روی Clone تصادفی و موقت است. Restore passwordهای اصلی را کپی نمی‌کند.

## ApplyDatabase — تغییر واقعی Schema، فقط بعد از تأیید

همه writerها و connectionهای IntelliJ (حتی SQL console) را ببندید؛ اسکریپت پیش از backup و قبل از apply تعداد sessionهای دیگر را می‌سنجد، ولی race با اتصال جدید را نمی‌تواند از بیرون PostgreSQL مطلقاً منع کند. maintenance window مسئولیت اپراتور است. برنامه در این مدت خاموش بماند. `ApplyDatabase` به Approval token شامل DB، target version و HEAD دوازده‌حرفی نیاز دارد. دوباره Backup و Restore و DryRun تازه انجام می‌دهد و بعد از بررسی drift سورس/history/schema/role و هویت کانتینر، فقط با Flyway روی DB اصلی migrate می‌کند. توکن مجوز است نه رمز.

```powershell
# نمونه مخصوص HEAD فعلی؛ فقط بعد از PASS واقعی DryRun و تصمیم مالک DB
powershell -NoProfile -ExecutionPolicy Bypass -File $m -Mode ApplyDatabase -RuntimeRole $runtime -Approve 'sakhtyar:V68:698280f36839'
# قبل از شروع Backend:
powershell -NoProfile -ExecutionPolicy Bypass -File $m -Mode Verify -RuntimeRole $runtime
# بعد از شروع Backend با همان login:
powershell -NoProfile -ExecutionPolicy Bypass -File $m -Mode Verify -RuntimeRole $runtime -CheckBackend
```

توجه: اگر پس از نصب سورس commit جدید ساخته شود، توکن نمونه دیگر معتبر نیست؛ از HEAD و target در گزارش Plan استفاده کنید. `Verify -CheckBackend` صرفاً GET readiness محلی و کنترل DB است و جای تست UI نیست. Startup initializer فقط history را قبل از Hibernate بررسی می‌کند، checksum کامل در Plan/Verify با Flyway بررسی می‌شود. هیچ Flyway auto-enable، ddl-auto update، dev seed، docker compose down -v، clean، repair، دستکاری مستقیم history یا restore روی DB اصلی وجود ندارد.

## آینده و CI

فایل Migration جدید باید با شماره صحیح و نام Vn__...sql در Git commit شود. ابزار به صورت پویا version و hash و Pending را کشف می‌کند. برای هر نسخه Pending جدید، یک entry بازبینی‌شده در `scripts/database/migration_runtime_policy.json` اضافه کنید؛ اگر جدول جدید ندارد `tables: {}` پس از بررسی صریح قابل قبول است. جدول جدید public بدون policy مسدود می‌شود. برای migrationهایی که ساختار دیگری تغییر می‌دهند باید سیاست/تست تخصصی اضافه شود؛ ابزار عمومی به تنهایی امنیت معنایی migration را ثابت نمی‌کند. `ci.yml` تعداد ثابت 66 را حذف و history واقعی CI را با inventory سورس تطبیق می‌دهد؛ smoke test خود CI هنوز DB موقت مصنوعی است. Spring initializer در local/development/production هنگامی که Flyway غیرفعال است قبل از Hibernate خطای قابل‌فهم Pending می‌دهد و از همان datasource login استفاده می‌کند. در صورت نبود SELECT روی history، fail closed. هیچ پروفایل قبلی یا تنظیمات seed تغییر نکرده است.

## Rollback / خطا

Source rollback فقط فایل‌های نصب‌شده و بدون تغییر بعدی را از Receipt خارج Git برمی‌گرداند. پس از commit از Git revert بازبینی‌شده استفاده شود. DB rollback خودکار عمداً وجود ندارد. اگر `live_apply=STARTED` یا schema fingerprint/verification بعد از apply شکست خورد، **بدون بررسی DB دوباره اجرا نکنید**؛ ممکن است بعضی نسخه‌ها commit شده باشند. DBA باید تاریخچه و backup را بررسی کرده و forward fix یا restore کنترل‌شده به محیط جداگانه انتخاب کند. اگر قطع برق clone را باقی گذاشت، فقط container دارای نام `sakhtyar-migration-clone-*` و label `sakhtyar.migration.clone=true` را پس از بررسی دستی حذف کنید؛ volume اصلی را هرگز حذف نکنید. `.lifecycle.lock` فقط وقتی پردازش قبلی قطعاً متوقف است، دستی پاک شود. Report آخرین JSON را نمایش می‌دهد؛ قبل از اشتراک‌گذاری شناسه‌ها/مسیرهای خصوصی را بررسی کنید. لاگ Flyway و backup محرمانه هستند.

## محدودیت‌های پذیرش

تست‌های Python آفلاین اجرا شده‌اند. تست Java در سورس افزوده شده ولی اجرای Maven/Spring، PowerShell 5.1، PostgreSQL/Flyway واقعی، restore و UI نیازمند Windows کاربر/CI است. وضعیت تحویل `READY_TO_VERIFY` است، نه COMPLETE. مقایسه fingerprint از dump نرمال‌شده محافظه‌کارانه است؛ اختلاف مشروع restore هم gate را می‌بندد و DBA باید بررسی کند. این ابزار فقط کانتینر محلی مشخص را هدف می‌گیرد؛ برای Production فرآیند مستقل مصوب لازم است.
