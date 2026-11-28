# مدیریت خودکار Migration دیتابیس در Startup ساخت‌یار

## هدف

در پروفایل `local`، پیش از ساخت DataSource و Hibernate، ساخت‌یار نسخه Migrationهای سورس و دیتابیس محلی را تطبیق می‌دهد. اگر دیتابیس عقب باشد، همان Lifecycle کنترل‌شده موجود را اجرا می‌کند: بررسی Git و Inventory، Flyway Validate، تهیه Backup، Restore آزمایشی، Clone ایزوله بدون شبکه، اجرای Migration روی Clone، کنترل Schema و مجوزهای Runtime، بررسی عدم تغییر دیتابیس اصلی و در نهایت اعمال Migration روی دیتابیس محلی.

این قابلیت Flyway خودکار Spring را روشن نمی‌کند. مقدار `spring.flyway.enabled=false` در همه پروفایل‌ها حفظ می‌شود.

## ترتیب Startup

1. `Phase09ProfileSafetyInitializer`
2. `LocalInfrastructureInitializer`
3. `DatabaseMigrationStartupInitializer`
4. `DatabaseMigrationPreflightInitializer` (فقط خواندن و کنترل تاریخچه)
5. ادامه ساخت Spring Context، DataSource و Hibernate

## سیاست پروفایل‌ها

| پروفایل | رفتار |
|---|---|
| `local` | ارتقای خودکار فقط روی Compose محلی `sakhtyar-local-db-1` و دیتابیس `sakhtyar`؛ بعد از عبور از تمام Gateها |
| `development` | ارتقای خودکار Startup خاموش؛ دیتابیس مشترک REAL/TEST فقط با فرآیند انتشار تأییدشده قابل ارتقا است |
| `production` | ارتقای خودکار Startup ممنوع؛ Migration از مسیر انتشار/تأیید جداگانه انجام می‌شود |

## اولین راه‌اندازی و سیاست مجوز Runtime

فایل `scripts/database/runtime_privileges.reviewed.json` در HEAD مبنا وجود ندارد. بنابراین اولین اجرای قابلیت، به‌جای اجرای Migration روی دیتابیس اصلی، `role_lifecycle.py --mode DraftClone` را اجرا می‌کند. این کار از کاتالوگ واقعی دیتابیس و در صورت وجود Migrationهای معوق، از یک Clone ایزوله Draft می‌سازد و Startup را متوقف می‌کند.

اپراتور باید:

1. فایل `runtime_privileges.DRAFT.json` را در مسیر خصوصی `DatabaseBackups/MigrationLifecycle/<run-id>/` بازبینی کند.
2. برای تک‌تک اشیای PostgreSQL تصمیم `ALLOW` یا `DENY`، فهرست حداقلی مجوزها و دلیل روشن تعیین کند. `reviewed=true` فقط پس از تکمیل بررسی واقعی مجاز است.
3. نسخه تأییدشده را با نام `scripts/database/runtime_privileges.reviewed.json` در مخزن قرار دهد و Commit کند.
4. نقش محدود `sakhtyar_runtime` را با `Invoke-SakhtYar-DatabaseRoleLifecycle.ps1` طبق مسیر `Audit → DraftPolicy/DraftClone → Plan → DryRun → ApplyRole → Verify` ایجاد/تأیید کند. ساخت یا اعطای نقش و مجوزها در Startup خودکار انجام نمی‌شود.
5. در فایل Secrets محلی، `SAKHTYAR_RUNTIME_DB_USER=sakhtyar_runtime` و `SAKHTYAR_RUNTIME_DB_PASSWORD` را تنظیم کند. رمز عبور هرگز در سورس یا Git قرار نگیرد.
6. برنامه را مجدداً Run کند.

این Gate اولیه عمدی است: کاتالوگ واقعی دیتابیس و مجوزهای نقش Runtime را نمی‌توان بدون بازبینی به‌صورت حدسی تأیید کرد.

## رفتار اجرای عادی پس از Bootstrap

- اگر نسخه دیتابیس جاری باشد، Flyway Validate و بررسی سیاست نقش Runtime اجرا می‌شود و Backup/Clone غیرضروری انجام نمی‌شود.
- اگر Migration جدید وجود داشته باشد، `migration_lifecycle.py --mode Startup` اجرا می‌شود.
- Migrationهای جدید باید در Git Commit شده باشند؛ تغییر محلی Migrationها پذیرفته نمی‌شود.
- Migrationهای قبلی نباید بازنویسی شوند؛ checksum mismatch باعث توقف می‌شود.
- Migrationهای جدید باید در `migration_runtime_policy.json` و سیاست Runtime بازبینی‌شده پوشش داده شوند. اشیای جدید بدون سیاست مجوز تأییدشده باعث توقف می‌شوند.
- دیتابیس فقط وقتی ارتقا می‌یابد که Backup قابل Restore، Clone ایزوله، Flyway Validate، بررسی Schema، کنترل نقش Runtime، کنترل عدم اتصال Clientهای دیگر و عدم Drift همگی موفق باشند.
- در صورت خطا پس از شروع Live Apply، Rollback خودکار یا Restore خودکار اجرا نمی‌شود؛ چون می‌تواند داده‌های جدید را از بین ببرد. عملیات متوقف و گزارش خصوصی برای بررسی DBA حفظ می‌شود.

## تنظیمات

در `application-local.yml`:

- `app.database-migration.startup-enabled=true` (قابل خاموش‌کردن با `APP_DATABASE_MIGRATION_STARTUP_ENABLED=false`)
- `app.database-migration.python-executable=python` (قابل تنظیم با `SAKHTYAR_DATABASE_MIGRATION_PYTHON`)
- `app.database-migration.timeout-seconds=3600` (محدوده مجاز 60 تا 7200)
- DataSource محلی از نقش `sakhtyar_runtime` استفاده می‌کند؛ رمز آن از Secrets محلی می‌آید.

`development` و `production` مقدار `startup-enabled=false` دارند. تلاش برای فعال‌کردن این قابلیت در این پروفایل‌ها توسط `Phase09ProfileSafetyInitializer` رد می‌شود.

## گزارش و عیب‌یابی

گزارش‌ها و Backupها بیرون از ریپازیتوری در مسیر `DatabaseBackups/MigrationLifecycle/` قرار می‌گیرند. گزارش ممکن است شامل نام اشیا، نسخه‌ها و اطلاعات Schema باشد؛ آن را عمومی نکنید. فایل‌های Backup، dump و log خصوصی هستند و نباید به Git اضافه شوند.

در صورت توقف، متن `[StartupMigration] BLOCKED` یا `[Migration] BLOCKED` را بخوانید و آخرین `report.json` خصوصی را بررسی کنید. هیچ Guard امنیتی را با روشن‌کردن Flyway یا Hibernate `ddl-auto=update` دور نزنید.
