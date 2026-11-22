# ساخت‌یار — فاز ۱۲: قراردادهای مشارکت و نسخه‌بندی

## مشخصات مبنا
- مخزن: `HAminzadeh/SakhtYar`؛ شاخه: `v2`.
- HEAD بررسی‌شده: `2d8f4dfaa8894705eb0750a37ed3d8468ce45535` (۲۰۲۶-۱۱-۲۲ ۱۱:۱۵:۴۴ UTC).
- آخرین Flyway هنگام بررسی: `V66`؛ Migration این بسته `V67` است.
- نصب‌کننده هیچ دستور Docker/PostgreSQL/Flyway اجرا نمی‌کند. **قبل از شروع مجدد Backend** باید V67 روی کلون جداگانه تست شود؛ چون اجرای عادی Spring Boot با Flyway روشن می‌تواند به دیتابیس متصل اصلی مهاجرت کند.

## ماتریس نقص، شواهد و اصلاح
| Path | وضعیت پیشین | شاهد واقعی | نقص | اولویت | اصلاح | اثبات |
|---|---|---|---|---|---|---|
| `ContractService.java` | Partial | `requireCase` فقط `existsById` | IDOR، عدم کنترل عضویت، scope و CASE_READ/WRITE در سرویس | P0 | CaseAccessService و بررسی REAL، role TESTER و مجوزها در تمام متدها | `ContractServiceSecurityTest` |
| `ContractService.java` | Partial | وضعیت و signedAt از DTO اعتماد می‌شد | ادعای امضای حقوقی بدون provider معتبر | P0 | فقط `DRAFT` و `signedAt=null`، legacy non-draft فقط خواندنی | `ContractInputPolicyTest` |
| `ContractService.review` | Missing | query بدون requireCase | افشای وضعیت و متن قرارداد پرونده دیگری | P0 | کنترل ACL پیش از query | تست منفی review |
| `ContractService.review` | Partial | `terms.toString().contains` | مثبت کاذب درباره وجود بند | P1 | بررسی کلید دقیق و مقدار غیرخالی | تست مرور ساختاری |
| `ContractService.create/update` | Partial | فاقد parent lock و flush پیش از audit | race ایجاد و تغییر هم‌زمان | P1 | قفل CaseRepository.findByIdForUpdate، saveAndFlush، unique DB | تست mock؛ concurrency واقعی DB هنوز نیازمند clone |
| `contract_record` | Partial | فقط `schema_version`، بدون نسخه مستقل | نبود تاریخچه ویرایش | P1 | جدول الحاقی `contract_revision` و GET نسخه‌ها | Unit و DB clone (معلق) |
| `UpsertContractRequest` | Partial | Map بدون limit | JSON نامعتبر/بزرگ/تودرتو | P1 | policy با اندازه، عمق، کلید و عدد معتبر | ۱۰ آزمون offline و تست‌های JUnit (اجرای Maven در Windows) |
| `backend/modules/contract/src/test` | Missing | نبود فایل در HEAD بررسی‌شده | فقدان رگرسیون | P1 | تست Service، Policy و HTTP | Maven هدف |

## معماری و مسیر داده
- HTTP `ContractController` → Application `ContractService` (همه مجوزها سروری) → `CaseRepository` و `CaseAccessService` → `ContractRecordRepository` و `ContractRevisionRepository` → PostgreSQL.
- AuditService هم‌تراکنش با ذخیره رکوردها اجرا می‌شود. payload آن صرفاً کد ثابت، status و شماره نسخه است؛ هیچ متن یا اطلاعات طرفین قرارداد به Audit فرستاده نمی‌شود.
- برای create و update، ابتدا قفل نوشتن سطر والد پرونده گرفته می‌شود. رکورد قرارداد و snapshot نسخه و audit در همان تراکنش هستند.
- با وجود کنترل قفل، PUT بدون هدر `If-Match` از نظر optimistic lost-update در سطح کلاینت هنوز ریسک دارد. این endpoint برای سازگاری با مشتریان قدیمی همچنان PUT بدون هدر را می‌پذیرد؛ کلاینت‌های جدید باید `If-Match: "<revisionNo>"` بفرستند. اجباری‌کردن این هدر پس از مهاجرت کلاینت‌ها توصیه می‌شود.
- `schemaVersion` موجود نسخه قالب JSON است و با `revisionNo` تاریخچه فرق دارد.

## API
مسیرهای قبلی بدون تغییر URL حفظ شده‌اند:
- `GET /api/v1/contracts/{id}`
- `GET /api/v1/contracts/cases/{caseId}`
- `POST /api/v1/contracts/cases/{caseId}` فقط پیش‌نویس
- `PUT /api/v1/contracts/{id}` فقط پیش‌نویس، پشتیبانی اختیاری از `If-Match: "2"`
- `GET /api/v1/contracts/cases/{caseId}/review` ساختاری نه حقوقی

مسیرهای افزوده‌شده، تحت همان `CASE_READ` موجود:
- `GET /api/v1/contracts/{id}/versions`
- `GET /api/v1/contracts/{id}/versions/{number}`

بهره‌برداری از وضعیت‌های `APPROVED`, `SIGNED`, `ARCHIVED` و ثبت امضاکننده **عمداً پیاده‌سازی نشده است**؛ چون Provider امضای معتبر، مدل رضایت و فرایند حقوقی تأییدشده مشاهده نشد. وضعیت‌های قدیمی غیراز DRAFT فقط خواندنی هستند.

## داده و Migration
- `V67__contract_draft_revision_ledger.sql` یک جدول جدید `contract_revision` با FK، Unique، Check، Index، trigger ضد UPDATE/DELETE و RLS وابسته به REAL parent ایجاد می‌کند.
- Migration هیچ سطر موجود REAL را تغییر نمی‌دهد؛ قراردادی که قبل از V67 ایجاد شده، در اولین ویرایش مجاز یک snapshot با رویداد `BASELINE_CAPTURED` دریافت می‌کند. این رویداد سند تاریخی زمان ایجاد نیست.
- حفاظت کامل RLS جدول `contract_record` در پروژه موجود نبود؛ Migration این فاز عمداً RLS آن جدول قدیمی را در سکوت تغییر نمی‌دهد. کنترل Application برای REAL/TEST وجود دارد؛ **DB hardening این جدول باید جداگانه روی clone بررسی شود**.
- جدول نسخه‌ها به‌صورت append-only است و با حذف قرارداد والد، برای جلوگیری از حذف تاریخچه، FK با `ON DELETE RESTRICT` عمل می‌کند. فرآیند حذف پرونده دارای قرارداد نیاز به سیاست نگهداری/retention بررسی‌شده دارد.
- هیچ نسخه یا متن «الزام حقوقی» از منابع نامعتبر ساخته نمی‌شود. وجود کلیدهای REQUIRED یک checklist فنی است، نه قانونی.

## امنیت و قلمرو داده
- `ROLE_TESTER` از داده REAL مسدود است، حتی اگر مجوز CASE_READ داشته باشد.
- شناسه UUID یک مجوز محسوب نمی‌شود؛ هر GET مستقیم بر اساس CaseAccessService مجوزسنجی می‌شود.
- هیچ عملیات فرستادن قرارداد به طرفین، دولت، ایمیل، پیامک یا سرویس بیرونی وجود ندارد.
- داده TEST به شکل ساختگی فقط داخل JUnit و Mockito استفاده می‌شود؛ تست‌ها هیچ DB واقعی ندارند.
- Audit شامل متن قرارداد، هویت اشخاص، کلیدها، پیوست یا امضای جعلی نیست.

## اجرای نصب
فایل ZIP و اسکریپت `Apply-SakhtYar-Contract-Phase12.ps1` را در `Downloads` بگذارید:
```powershell
$script="$env:USERPROFILE\Downloads\Apply-SakhtYar-Contract-Phase12.ps1"
foreach($mode in 'SelfTest','Audit','DryRun','ApplySource','Verify') {
  powershell -NoProfile -ExecutionPolicy Bypass -File $script -Mode $mode
  if($LASTEXITCODE -ne 0){throw "Stopped: $mode"}
}
```
**هشدار:** چون V67 همراه سورس نصب می‌شود، قبل از اجرای اپلیکیشن Spring Boot که Flyway آن فعال است، ابتدا migration را روی clone ایزوله بررسی کنید. `ApplySource` خودش Flyway یا دیتابیس را اجرا نمی‌کند.

## Verify
- `Verify` فقط تست‌های allowlist: `ContractInputPolicyTest`, `ContractServiceSecurityTest`, `ContractControllerHttpTest` را با Maven آفلاین و `clean test` اجرا می‌کند؛ هیچ تست یکپارچگی DB یا پردازش خارجی به‌صورت پنهان اجرا نمی‌شود.
- وجود Maven 3.x، Java 21 و وابستگی‌های Maven در کش محلی ضروری است.
- تست‌های Unit/Mock/HTTP در محیط تحویل باید واقعاً اجرا شوند. وضعیت آنها تا آن زمان `NOT_RUN` است.
- آزمون مستقل Java 21 با stubs فقط کلاس Policy واقعی را کامپایل کرده و ۱۰ سناریو را در همین محیط اجرا کرده است: `PASSED (10/10)`، اما جایگزین Maven نیست.
- بعد از نصب و تأیید بر روی clone، اجرای E2E UI و DB RLS نیازمند سناریوی جداگانه است.

## Rollback
- Backup فایل‌های جایگزین‌شده در پوشه `SakhtYar-PatchBackups/Contract-Phase12-*` **خارج از سورس** ساخته می‌شود. فهرست فایل‌های جدید در `state.json` ثبت می‌شود.
- `Rollback -BackupDir '<مسیر نمایش داده‌شده در ApplySource>'` فقط در صورت تطبیق هش فایل‌های نصب‌شده مجاز است؛ تغییرات جدید کاربر هرگز حذف یا بازنویسی نمی‌شود.
- Rollback سورس به معنی Rollback دیتابیس نیست. اگر V67 روی PostgreSQL اعمال شده باشد، به‌دلیل نگهداری تاریخچه باید طرح بازیابی DB جدا تهیه شود؛ **Flyway reverse خودکار ارائه نشده است**.

## ریسک‌های باقیمانده و Gate
1. Migration V67 هنوز روی clone PostgreSQL اجرا نشده: `BLOCKED` برای تأیید DB.
2. هیچ Provider و مدل معتبر امضای الکترونیکی، تأیید حقوقی، هویت امضاکنندگان یا امضای طرفین کشف نشده: خارج از محدوده این فاز، **انجام نشده**.
3. تست Browser E2E و DataScope/RLS از مسیر واقعی API: `NOT_VERIFIED`.
4. PUT بدون `If-Match` برای سازگاری باز مانده و احتمال overwriting ادیت قدیمی بین کاربران وجود دارد.
5. ارتباط رسمی طرفین با Owner/Builder به قرارداد امضاشده و اعتبار مالکیت نباید استنتاج شود؛ مدل حقوقی و پروفایل امضاکنندگان هنوز طراحی نشده است.
6. در این محیط Maven/PowerShell 5.1/PostgreSQL در دسترس نیست: وضعیت نهایی `READY_TO_VERIFY` و نه Fully Verified.
