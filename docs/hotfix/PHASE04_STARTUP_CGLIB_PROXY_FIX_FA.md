# رفع توقف Startup به علت final بودن Controller ممیزی PostgreSQL

- مبنا: Git `v2`، Commit `a27032dd5f94daf767f7001f457db47284eaa503` (22 نوامبر 2026).
- شاهد لاگ: `Cannot subclass final class com.sakhtyar.quality.Phase02SharedDatabaseAuditController`.
- ریشه: متد `inspect()` با `@Transactional(readOnly = true)` روی یک Controller از نوع `public final class` قرار دارد. پروکسی کلاس‌محور Spring AOP/CGLIB نمی‌تواند از کلاس final ارث‌بری کند؛ به همین دلیل Bean ساخته نشده و ApplicationContext از بین می‌رود.
- اصلاح: فقط `public final class` به `public class` تبدیل شد. مجوز `USER_MANAGE`، رد `ROLE_TESTER`، SQL فقط‌خواندنی، و نتیجه‌های `globalIsolationVerified=false` / `businessE2eWritesAuthorized=false` حفظ شدند.
- تست: `Phase02SharedDatabaseAuditCglibTest` با ProxyFactory واقعی Spring AOP، توانایی ساخت Proxy کلاس‌محور را بررسی می‌کند؛ همچنین مسیر GET، annotation فقط‌خواندنی و رد درخواست بدون مجوز کنترل می‌شود.
- تست‌های آفلاین Python نیز برداشتن `final` و حفظ قراردادهای مجوز و ایمنی را بررسی می‌کنند.

## دستورها

1. فایل ZIP و PowerShell را در `Downloads` قرار دهید.
2. `-Mode DryRun`
3. `-Mode ApplySource`
4. `-Mode Verify` (اجرای آفلاین تست‌های Java/Maven روی Windows؛ `mvn.cmd` باید در PATH باشد).
5. برنامه را از IntelliJ مجدداً Build و اجرا کنید و برای Backend منتظر `Started SakhtYarApplication` شوید.

## موارد امنیتی

- نصب‌کننده Docker، دیتابیس، PostgreSQL، Flyway، Redis، MinIO، سرویس‌های نظارتی، یا Volumeها را تغییر نمی‌دهد.
- هیچ migration اضافه یا تغییر داده‌ای اجرا نمی‌شود.
- فقط Controller مشخص تغییر می‌کند؛ چهار فایل سند/تست/ممیزی جدید اضافه می‌شوند.
- اگر سورس مبنا یا فایل‌های جدید محلی با بسته تفاوت داشته باشند، قبل از هر تغییر متوقف می‌شود.
- برای بازگشت از Backup ایجادشده توسط نصب‌کننده استفاده کنید؛ Rollback فایل‌های تغییریافته توسط دیگران را بازنویسی نمی‌کند.
- معماری یک PostgreSQL مشترک با جداسازی منطقی REAL/TEST کاملاً حفظ می‌شود.

## شواهد و محدودیت

- لاگ قبلی Flyway با 66 migration معتبر و HikariPool سالم گزارش داده؛ استارت واقعی پس از نصب هنوز باید توسط کاربر تأیید شود.
- تست Java/Maven در محیط تهیه ZIP با وابستگی‌های واقعی Spring اجرا نشده است. فقط تست‌های Python و شبیه‌سازی فایل اجرا می‌شوند.
- پیام‌های Redis repository scanning که در لاگ INFO هستند علت توقف ثبت‌شده نیستند.
