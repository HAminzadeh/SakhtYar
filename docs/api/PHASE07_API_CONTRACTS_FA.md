# فاز ۰۷/۴۰ — قرارداد API و یکپارچگی بین ماژول‌ها

**مبنای خوانده‌شده از GitHub:** `37135d2005296ac6978abb9bd2c91a4c3496ecf1` (`v2`، ۲۰۲۶-۱۱-۲۲ 06:49:25 UTC).

## وضعیت واقعی و دامنه تغییر

این بسته تنها یک فایل موجود از Shared Kernel را اصلاح می‌کند، یک تست MockMvc بدون دیتابیس می‌افزاید و ابزارهای ممیزی/گزارش‌گیری بین Backend و Frontend را نصب می‌کند. تمام موارد Source-only یا MockMvc هستند. **هیچ نتیجه‌ای درباره جداسازی داده واقعی/آزمایشی، امنیت HTTP در محیط اجرا، Testcontainers، کارکرد کسب‌وکار یا اتصال PostgreSQL از این تست‌ها اثبات نمی‌شود.**

| مسیر | شاهد / قرارداد | اقدام |
| --- | --- | --- |
| `backend/modules/shared-kernel/.../ApiExceptionHandler.java` | در `HEAD` تنها IllegalArgumentException، ResponseStatusException، DTO validation هندل می‌شوند | JSON نامعتبر، پارامتر اجباریِ گمشده، خطای تبدیل نوع، method validation به فرم پایدار تبدیل می‌شود |
| `frontend/src/api/client.ts` | کلاینت `status/code/message/fieldErrors` می‌خواند | قرارداد موجود حفظ می‌شود؛ تغییر API کلاینت لازم نیست |
| `backend/modules/identity/.../AuthController.java` | Login/Refresh/Me/CSRF وجود دارند | Golden Route checks؛ منطق Auth دست‌نخورده |
| `backend/modules/casefile/.../CaseController.java` | GET/POST پرونده، GET جزئیات | Golden Route checks؛ DTO دست‌نخورده |
| `backend/modules/property/.../PropertyController.java` | GET/PUT property | Golden Route checks |
| `backend/modules/owner/.../OwnerController.java` | GET/POST owner | Golden Route checks |
| `backend/modules/geo/.../GeoController.java` | Geocode/Reverse | Golden Route checks |
| `backend/modules/audit/.../AuditReadController.java` | Read-only Audit GET | Golden Route check |
| `frontend/src/api/types.ts` | Permission, UserRole, UserStatus, CaseStatus | مقایسه Union با Java enum |
| `backend/app/src/main/resources/db/migration/V1..V66` | ۶۶ فایل در درخت GitHub | بدون هیچ Migration جدید یا بازنویسی |

## نمودار وابستگی

`Frontend API Client → Spring MVC Controllers → Domain Services → Persistence` و
`Shared Kernel ApiExceptionHandler ← همه کنترلرهای ماژول‌ها`.

اسکنر `scripts/contracts/phase07_api_inventory.py` مسیرهای استاتیک Spring و فراخوانی‌های صریح TS/TSX را می‌خواند و **دو خروجی** ایجاد می‌کند:

- `phase07-api-audit.json`: فهرست تعداد Routeها، Golden Gates، اختلاف Enumها، فراخوانی‌های تطبیق‌نیافته، Routeهای احتمالی تکراری و موارد نامشخص.
- `phase07-openapi-source-draft.json`: سند JSON با ساختار OpenAPI 3.1 و `x-contract-status=UNVERIFIED_RUNTIME` برای هر عملیات. **این خروجی OpenAPI رسمی اجرایی یا DTO Schema قطعی نیست.**

## تغییرات خطاها (non-breaking)

| وضعیت | `code` | پیام بدون افشای جزئیات |
| --- | --- | --- |
| JSON نامعتبر | `MALFORMED_JSON` | `Malformed JSON request.` |
| پارامتر اجباری مفقود / تبدیل نوع نامعتبر | `INVALID_ARGUMENT` | `Invalid request parameter.` |
| خطای اعتبارسنجی Method/Constraint ورودی | `VALIDATION_FAILED` | `Validation failed.` |
| خطای اعتبارسنجی خروجی Handler Method | `HTTP_500` | `Request failed.` |

موارد قبلی از جمله `INVALID_ARGUMENT` برای IllegalArgumentException، `VALIDATION_FAILED` برای Bean Validation و `fieldErrors` بدون تغییر می‌مانند. هیچ خطای parser/raw SQL/PII به پاسخ جدید افزوده نمی‌شود. برای JSON نامعتبر عمداً `fieldErrors={}` باقی می‌ماند.

## محدودیت‌های اسکن استاتیک

این اسکنر روی literal annotations و فراخوانی‌های literal تمرکز دارد؛ Routeهای محاسبه‌شده، Java inheritance/multiple path mappings، HandlerFunction و TypeScript endpointهای ساخته‌شده در Runtime ممکن است در گزارش ناقص باشند. **Unmatchedهای خارج از Golden فقط `POTENTIAL_MISMATCH` هستند، نه اثبات 404.** مجوز هر Route از `SecurityFilterChain` به‌صورت رسمی استنتاج نمی‌شود. خروجی را با `/v3/api-docs` یا `RequestMappingHandlerMapping` در Runtime و درخواست‌های واقعی مقایسه کنید.

## تست و پذیرش

- `Audit` و `DryRun`: بدون تغییر سورس/DB.
- `ApplySource`: تغییر Source-only با محافظ Hash فایل GitHub، Backup مجزا و Fail-closed.
- `Verify`: کنترل Source، هفت تست منفی/مثبت Python، Maven `-o -pl modules/shared-kernel -am test` و **به‌صورت پیش‌فرض** `npm run build` (اگر `node_modules` موجود باشد). عدم نصب Node/npm به‌عنوان BLOCKED گزارش می‌شود، نه PASS جعلی.
- `-SkipMaven` و `-SkipFrontend` فقط برای تشخیص محدود هستند. نتیجه آن‌ها هرگز به معنی Build کامل نیست.
- `Rollback`: با Backup اعلام‌شده توسط ApplySource، فقط فایل‌های تغییر کرده توسط Patch بازگردانده می‌شوند؛ هر اختلاف هش موجب توقف می‌شود.
- `git diff --check`: برای فایل اصلی اصلاح‌شده و کنترل whitespace فایل‌های جدید.

### Gateهای هنوز ناتمام

1. تست‌های HTTP واقعی با نقش‌های ADMIN/TESTER/کاربر عادی: 401/403، Cookie، CSRF، Refresh Token.
2. اجرای Testcontainers بر روی PostgreSQL clone دارای migrationهای V1 تا V66 و نقش RLS محدود؛ اثبات no cross-workspace reads/writes.
3. Idempotency و هم‌زمانی درخواست‌های نوشتنی در TEST workspace فقط بعد از Safety Gate مستقل.
4. OpenAPI رسمی Runtime شامل DTO schemas، status codes، security schemes و backward-compatibility diff.
5. E2E مرورگر RTL/Farsi و خطاهای 4xx/5xx.

**قید قطعی:** نقش برنامه TESTER فقط QUALITY_VIEW و QUALITY_RUN_SAFE دارد، معادل PostgreSQL role نیست؛ این بسته هیچ دستور SQL یا عملیات Docker اجرا نمی‌کند.
