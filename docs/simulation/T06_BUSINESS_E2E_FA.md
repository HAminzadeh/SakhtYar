# T06 | موتور اجرای واقعی E2E ساخت‌یار — نسخه نخست

این نسخه دو موتور مجزا دارد: `READ_ONLY_PREFLIGHT` قدیمی که ۹۶ بررسی PostgreSQL، Schema، API Mapping و Gate را انجام می‌دهد؛ و `BUSINESS_E2E` جدید که ۲۴ شناسه را **واقعاً در صف اجرای مستقل** قرار می‌دهد و برای هر کدام نتیجه مستقل ثبت می‌کند.

## پوشش واقعی

- `E2E-14`: اجرای واقعی محدود به **۴ جدول** (`construction_case`, `property`, `property_owner`, `case_document`) با ایجاد یک پرونده TEST، یک ملک، دو مالک، بازیابی رکوردها و آزمایش نامرئی بودن TEST در REAL؛ این خدمت به سرویس آماده T05 متکی است.
- `E2E-01` تا `E2E-13` و `E2E-15` تا `E2E-24`: هنوز **پیاده‌سازی E2E ندارند** و نتیجه صادقانه `BLOCKED` ثبت می‌شود. نباید `PASSED` شوند؛ فقط بررسی اولیه مستقل V3 قبلاً اجرا شده است.
- E2E-14 هنوز آپلود واقعی فایل، API عمومی پرونده، چرخه معامله و پرداخت را پوشش نمی‌دهد. `PASSED` در این فاز صرفاً اثبات Fixture چهارجدولی است.

## API و جدول‌ها

Migration `V62__simulation_e2e_execution.sql` جداول `simulation_e2e_run`, `simulation_e2e_result`, `simulation_e2e_step` را اضافه می‌کند. Endpointهای جدید مدیر:

- `GET /api/v1/admin/simulations/e2e/coverage`
- `POST /api/v1/admin/simulations/e2e/runs`
- `GET /api/v1/admin/simulations/e2e/runs?workspaceId=<UUID>`
- `GET /api/v1/admin/simulations/e2e/runs/<UUID>`

برای درخواست نوشتن، `workspaceId`, `requestKey`, `scenarioIds` ارسال می‌شود. فقط کاربر دارای `USER_MANAGE` مجاز است. درخواست با کلید تکراری به اجرای قبلی ارجاع می‌دهد.

**برای E2E-14 باید:** V58 تا V62 نصب شده باشد؛ حساب DB برنامه `SUPERUSER`/`BYPASSRLS` نباشد؛ FORCE RLS روی چهار جدول برقرار باشد؛ جدول fixture وجود داشته باشد؛ فضای آزمایشی ACTIVE باشد؛ و دیتابیس نسخه پشتیبان داشته باشد. در غیر این صورت سناریو `BLOCKED` می‌شود و علت آن در جزئیات ثبت می‌گردد.

## اجرای بدون رابط کاربری

Node.js نسخه 20 به بالا؛ هیچ پکیج npm لازم نیست. نمونه دستور PowerShell پس از ورود به پروژه:

```powershell
$env:SAKHTYAR_BASE_URL = 'http://localhost:8080'
$env:SAKHTYAR_TEST_WORKSPACE_ID = '<workspace-uuid>'
$env:SAKHTYAR_COOKIE = '<authenticated-admin-cookie-from-your-session>'
$env:SAKHTYAR_CSRF_TOKEN = '<matching-xsrf-token>'
$env:SAKHTYAR_ENABLE_TEST_WRITE = '1'
$env:SAKHTYAR_E2E_IDS = 'E2E-14'
node .\tests\e2e\run-business-e2e.mjs
```

برای همه ۲۴ سناریو، متغیر `SAKHTYAR_E2E_IDS` را پاک کن. CLI با وضعیت خروج ۲ علامت می‌دهد که سناریوهای مسدود یا ناموفق وجود دارند؛ تنها با `SAKHTYAR_ACCEPT_BLOCKED=1` می‌توان برای **ثبت فهرست پوشش** این وضعیت را پذیرفت، نه برای اعلام قبولی E2E.

هرگز Cookie، توکن یا رمز عبور را در پیام Git یا Commit ثبت نکن.

## الزامات تکمیل ۲۳ سناریو

هر سناریو به Fixtureها، قرارداد HTTP عملیاتی، assertionهای واقعی بر محاسبات و مجوز، محافظ عملیات جانبی و پاکسازی/ماندگاری TEST نیاز دارد. اضافه کردن نتیجه `PASSED` فقط پس از پیاده‌سازی این مسیرها مجاز است. برای تست مرورگر نیز Playwright و محیط ایزوله باید در مراحل بعدی افزوده شوند.

**توجه:** کامپایل یا مهاجرت دیتابیس در این بسته در محیط کاربر هنوز اجرا/تأیید نشده است.
