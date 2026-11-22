# فاز ۰۷ — اجرای مستقل ۲۴ سناریو با جداسازی موجود REAL/TEST

این نسخه **۲۴ Executor تشخیصی واقعی و قابل اجرا** به `SimulationCenterPage` اضافه می‌کند. این اجرا **معادل ۲۴ آزمون تجاری End-to-End موفق نیست** و وضعیت کسب‌وکار سناریوها عمداً `BLOCKED` می‌ماند.

## چرا مسیر جداگانه لازم بود؟
در موتور قبلی `SimulationBusinessE2EController`، سناریوهایی که وابستگی تجاری آن‌ها PASS نشده، اصلاً بررسی نمی‌شدند؛ لذا از گزارش نمی‌شد آمادگی خود آن سناریو را مستقل تشخیص داد. مسیر جدید برای هر سناریو، حتی وقتی پیش‌نیاز تجاری آن BLOCKED است، کنترل‌های مستقل اجرا می‌کند.

## قابلیت‌ها
- ۲۴ کلاس Executor مجزا (یک کلاس برای هر E2E-01 .. E2E-24)، و آزمون کامل بودن Registry.
- کنترل اتصال PostgreSQL، موجود بودن جدول‌ها، ثبت Route واقعی در Spring MVC؛ **فقط SELECT/اطلاعات کاتالوگ**.
- ممیزی ایزوله‌سازی و ساختار دامنه بدون اجرای درج یا تغییر کسب‌وکار.
- اجرای واقعی قراردادهای درون‌حافظه‌ای Phase 02: UUID قطعی Fixture، جداسازی Namespace، Canary حافظه‌ای Redis، پاک‌سازی دقیق Canary و منع خروج واقعی شبکه.
- ثبت دلایل BLOCKED و خواسته‌های مشخص هر سناریو، همراه ۶ گام و وضعیت هر گام.
- اجرای کل ۲۴ سناریو یا فقط یک سناریو از تب «تشخیص مستقل ۲۴ سناریو» فرم Simulation Center.
- دانلود JSON کامل (بلافاصله در Browser) برای ارسال و تحلیل.
- گزارش‌ها فقط در حافظه Backend با ظرفیت ۴۰ و حداکثر عمر یک ساعت هستند؛ بعد از Restart از بین می‌روند. **JSON را همان لحظه دانلود کنید.**
- همان تابع `SimulationWorkspaceAuthorizationService.requireActiveOwner` و اختیار `USER_MANAGE` برای Workspace موجود استفاده می‌شود. هیچ Workspace جدید یا DB Clone ساخته نمی‌شود.

## مرزهای امنیتی
- هیچ SQL write، Flyway، Seed، Role، RLS، Docker، Clone، Restore، TEST cleanup یا اتصال خارجی در Executorهای جدید وجود ندارد.
- اجرای محدود E2E-14 قدیمی و Business E2E Controller بدون تغییر می‌مانند. فرم جدید با آن‌ها مستقل است.
- خروجی HTTP واقعی مثبت/منفی، Query با Restricted Login، Playwright تجاری، Snapshot پایدار و Release Artifact هنوز لازم‌اند.
- خروجی `source_commit=UNVERIFIED` ممکن است تا وقتی BuildProperties دارای SHA معتبر نباشد باقی بماند.
- نتایج آمادگی مرحله‌ای نباید به‌عنوان درصد تکمیل محصول یا درصد موفقیت Business E2E تفسیر شوند.

## تست پس از نصب
1. `node tests/phase07/source_contract.mjs`
2. `cd backend && mvn -B -ntp -pl modules/quality -am -Dtest=Phase07DiagnosticExecutorsTest -Dsurefire.failIfNoSpecifiedTests=false test`
3. `cd backend && mvn -B -ntp -DskipTests -DskipITs package`
4. `cd frontend && npm run build`
5. **قبل از Restart دستی Backend در IntelliJ، متغیر محیطی `APP_DEV_SEED_ENABLED=false` را در Run Configuration تنظیم کنید.** چون `DevSeedRunner` با `app.dev-seed.enabled=true` فایل `seed-demo.sql` را اجرا می‌کند و دیتابیس مشترک REAL/TEST است. خود لانچر Backend را اجرا نمی‌کند.
6. اجرای Backend از IntelliJ و تب «تشخیص مستقل ۲۴ سناریو»، انتخاب Workspace فعال و دانلود JSON.

## خروجی قابل پذیرش
- `scenario_count=24`
- برای هر سناریو `steps` با ۶ کنترل و `blockers` وجود داشته باشد.
- `diagnosticExecutorCount=24` و `businessScenarioPassedCount=0` تا زمان وجود شواهد تجاری واقعی.
- عدم نشت داده REAL و عدم فراخوانی outbound، با کنترل‌های موجود Phase02؛ **تست Fake به‌تنهایی اثبات جداسازی PostgreSQL واقعی نیست**.
