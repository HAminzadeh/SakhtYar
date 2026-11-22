# فاز ۰۹ از ۴۰ — ملک، مشخصات فنی و پیوند پرونده

## مبنای سورس و شواهد

- مخزن: `HAminzadeh/SakhtYar`، شاخه `v2`.
- HEAD بازبینی‌شده: `5d35f6dcc743835e4a53abf436987c4e2f40ad52`، 2026-11-22T07:26:55Z.
- آخرین Migration موجود در HEAD: `V66`؛ این بسته هیچ Migration ندارد.
- Schema: `V3__property_and_owner_schema.sql` دارای `property.case_id` با `UNIQUE` و FK است؛ `V4` مختصات را به PostGIS Point تبدیل می‌کند؛ `V6` محدوده اعداد و JSONB را تعریف می‌کند؛ `V60` جداسازی هسته REAL/TEST را تعریف می‌کند. تغییر یا تکرار آنها ممنوع است.

## ماتریس اثبات و اقدام

| فایل/سرویس | وضعیت قبلی | اقدام فاز ۰۹ | تأیید |
|---|---|---|---|
| `PropertyService.requireCase` | فقط بررسی REAL و TESTER؛ مستقیم ACL را صدا نمی‌زند | اجرای `CaseAccessService.requireRead/requireWrite` | JUnit direct-call/IDOR |
| `PropertyService.upsert/mergeFacts` | بدون قفل والد | `CaseRepository.findByIdForUpdate` پیش از دسترسی به Property | JUnit قراردادی؛ رقابت PostgreSQL باز |
| `CaseLifecycle` | آرشیو در Case PUT محافظت می‌شد، نه Property | بررسی archived در mutation ملک | JUnit منفی |
| Property measurements | امکان عدد با scale بیش از ستون و مختصات تک‌فیلدی | `PropertyNumeric.validate` | Java21 smoke + JUnit |
| `mergeFacts` | رشته ارقام فارسی با BigDecimal parser خطا می‌دهد | parser فارسی/عربی با حفظ SI | Java21 + JUnit |
| `mergeAttributes` | اندازه patch کنترل شده، اندازه نهایی پس از merge خیر | سقف بر Map نهایی | JUnit موردنیاز |
| `PropertyPanel` | `Number` با ارقام فارسی NaN تولید می‌کند و JSON آنها را null می‌کند | تبدیل محدود و Fail-closed فرم | Node test + npm build |
| `PropertyRepository` | `findByCaseId` و `existsByCaseId` | بدون تغییر؛ reuse | JUnit reflection |
| Owner/Geo/Regulation/Feasibility | Property-ID و مقادیر عددی موجود را مصرف می‌کنند | DTO یا واحدها تغییر نمی‌کند | Build downstream، HTTP E2E باز |

## طراحی و مدل تهدید

- مسیر `GET /api/v1/cases/{caseId}/property`: ابتدا case واقعی و مجوز خواندن از AccessService؛ سپس کوئری Property.
- مسیرهای `PUT` و `PATCH`: ابتدا قفل بدبینانه ردیف `construction_case` و بررسی REAL، مجوز نوشتن و وضعیت غیرآرشیوی؛ بعد خواندن/ایجاد property، اعتبارسنجی و همگام‌سازی Snapshot پرونده در همان تراکنش.
- READ و WRITE را با نقش صرف `PROPERTY_*` نمی‌توان جایگزین عضویت و مالکیت پرونده کرد. مسیرهای Nested همچنان در MVC Interceptor نیز محافظت می‌شوند. نقش `TESTER` به REAL مجوز ندارد.
- قفل والد با `UNIQUE(property.case_id)` باعث می‌شود **کدهای این سرویس** ایجاد هم‌زمان یک ملک را سریال کنند. مسیرهای مستقل دیگر باید جدا بررسی شوند. قفل بدبینانه جلوی ویرایش مبتنی بر Snapshot قدیمیِ کلاینت را تضمین نمی‌کند.
- Geometry موجود **نقطه WGS84** است؛ مختصات طول/عرض باید با هم یا هیچ‌یک ثبت شوند، مقادیر در بازه مجاز و با scale حداکثر ۷ رقم باشند. سیستم هنوز مدل قطعه زمینِ چندضلعی و اعتبار رسمی پلاک ثبتی ندارد؛ آنها را جعل نکردیم.
- متراژها به مترمربع و طول‌ها به متر، بدون تغییر در API، نگهداری می‌شوند. سال ساخت همان قرارداد موجود 1000–2500 است و تبدیل تقویم انجام نمی‌شود.
- کدهای ثبتی مشابه در پرونده‌های دیگر **بدون تصمیم کسب‌وکاری و اثبات مالکیت** نباید به‌صورت سراسری Unique شوند؛ هر پرونده یک ملک دارد.
- ثبت Audit همچنان با تراکنش موفق عملیات همراه است؛ محرمانگی Payload مطابق فاز ۰۶ است.
- نه Migration جدید و نه ایجاد/حذف PostgreSQL Role، تغییر داده REAL/TEST، RLS، Docker و اتصال بیرونی انجام می‌شود.

## سازگاری

- شکل JSON، مسیرها، Enumها و واحدهای عمومی تغییر نکرده‌اند.
- اضافه‌شدن Validation ممکن است داده نامعتبر/مختصات ناقص قدیمی را هنگام **ویرایش بعدی** با HTTP 400 رد کند. چنین رکوردهایی باید با بررسی انسانی و تصحیح آگاهانه اصلاح شوند، نه پاک‌سازی خودکار.
- از جاوا ۲۱، Spring Boot 4.1.1 و Hibernate Validator موجود استفاده شده؛ وابستگی تست `spring-boot-starter-test` تنها در scope تست افزوده شده است.
- React/Vite بدون تغییر HTTP قرارداد باقی می‌ماند؛ اعداد فارسی قبل از JSON به Number استاندارد تبدیل می‌شوند. ورودی نامعتبر پیش از Submit مسدود است.

## آزمون‌ها و محدودیت پذیرش

1. `mvn -o -pl modules/property -am test` در فولدر `backend`: تست واحد/Mockito و کلاس‌های وابسته.
2. `node --experimental-strip-types --test tests/property/property-numeric.test.mjs` در ریشه مخزن: رفتار ارقام فارسی.
3. `npm run build` در `frontend`: بررسی TypeScript/Vite و Caller موجود.
4. آزمون PostgreSQL/Testcontainers **روی دیتابیس موقت مجزا** برای FK، UNIQUE، RLS، رکورد TEST، تداخل دو Upsert و قفل والد ضروری است؛ در نصب سورس اجرا نمی‌شود.
5. HTTP Integration با creator/active-member/nonmember و تست UI مرورگر هنوز نیازمند اجرای ایزوله است.
6. تا تکمیل این Gateهای Runtime، وضعیت `READY_TO_VERIFY`/`RUNTIME_GATES_OPEN` است؛ فقط PASS واقعی چاپ‌شده از Maven/Node ملاک موفقیت می‌باشد.

## نصب و بازگشت

- دو فایل ZIP و Launcher PowerShell 5.1 را کنار هم در Downloads ذخیره کن.
- `Audit` و `DryRun` بدون تغییر سورس؛ `ApplySource` پس از بررسی HEAD و hash، Backup کامل در `SakhtYar-PatchBackups/PropertyPhase09` می‌گیرد؛ `Verify` Maven/Node/npm را اجرا می‌کند.
- `Rollback` نیاز به BackupDir اعلام‌شده در `ApplySource` دارد؛ اگر فایل پس از نصب تغییر کند یا HEAD جابه‌جا شود، بازگشت به‌صورت Fail-closed متوقف می‌شود.
- `git diff --check`، خروجی JUnit و Node را پس از Verify بررسی کن؛ قبل از آن Commit نکن.
