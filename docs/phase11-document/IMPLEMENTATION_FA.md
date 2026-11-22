# فاز ۱۱ ساخت‌یار — اسناد و ذخیره‌سازی فایل (Document)

## مبنا و محدوده

- مخزن: `HAminzadeh/SakhtYar`، شاخه `v2`.
- HEAD مستقیم GitHub در زمان بررسی: `f3862bbd213d1ba625f6c3c30beb4f8444185fa5` (۲۰۲۶-۱۱-۲۲ ۰۸:۱۴:۴۶ UTC).
- آخرین Migration دیده‌شده: `V64`؛ **در این بسته هیچ Migration جدید یا اصلاحی وجود ندارد**.
- این بسته «تغییر سورس» است و **هیچ تغییری در دیتابیس یا MinIO اجرا نمی‌کند**.
- مسیر پروژه روی سیستم کاربر: `D:\ChatGPT_Projects\SakhtYar\source`.

## بررسی سورس واقعی (و نه تخمین از شمارش فایل)

| مسیر | وضعیت مشاهده‌شده در HEAD | شکاف / تصمیم | اولویت |
|---|---|---|---|
| `backend/modules/document/.../DocumentController.java` | دانلود با `Content-Disposition: attachment` و نام UTF-8 | افزودن `nosniff` و `no-store` به پاسخ دانلود | P1 |
| `backend/modules/document/.../DocumentService.java` | ACL پرونده و کنترل REAL/TEST وجود دارد؛ کنترل‌های DOCUMENT_READ/WRITE فقط در HTTP فیلتر بود | افزودن کنترل مجوز داخل Service و احراز هویت واقعی uploader؛ قفل والد | P0 |
| همان، `upload()` | نوشتن شیء MinIO پیش از پایان تراکنش؛ احتمال یتیم شدن شیء هنگام شکست DB/Commit | callback پاکسازی پس از rollback و پاکسازی best-effort هنگام خطای نیمه‌کاره‌ی MinIO، بدون افشای کلید/نام در Log | P1 |
| همان، `download()` | کنترل مسیر مالکیت شیء قبل از فراخوانی MinIO وجود داشت | کنترل صریح DOCUMENT_READ در سرویس و خطای NOT_FOUND یکنواخت | P0 |
| `Phase03DocumentSafety.java` | محدودیت ۲۵ MiB، بررسی magic برای PDF/PNG/JPEG/WEBP و نام امن وجود داشت؛ برای Office تنها سرآیند PK تست می‌شد | بررسی محدودشده ساختار ZIP سند DOCX/XLSX، جلوگیری از ZIP bomb، traversal و VBA | P1 |
| `frontend/src/features/case/DocumentsPanel.tsx` | دکمه بارگذاری `.doc/.xls/.zip` را می‌پذیرفت، Backend رد می‌کرد | هماهنگ‌سازی پسوندها و پیام فارسی، کنترل حجم پیش از ارسال | P2 |
| `DocumentRepository` / `DocumentEntity` / `StorageProperties` | مدل شناسه پرونده، هش SHA-256، object key و API موجود | بدون تغییر Schema/API/DTO؛ استفاده مجدد | Complete |
| `src/test/.../Phase03DocumentSafetyTest.java` | پنج آزمون واحد از قبل وجود داشت (برخلاف آمار تاریخی صفر) | حفظ تست‌های قبلی و افزودن تست‌های مستقل فاز ۱۱ | P1 |
| `CaseAccessService`, `CaseLifecycle` | اجازه خواندن به مالک/عضو مجاز؛ نوشتن فقط خالق/ادمین مجاز و عدم ویرایش پرونده ARCHIVED | استفاده مجدد، بدون پیاده‌سازی دوباره | Complete |

## معماری و جریان داده

**Upload**: `Spring Security` → `DocumentController` → `DocumentService.requirePermission(DOCUMENT_WRITE)` → قفل pessimistic روی `construction_case` → ACL و REAL-scope → کنترل ARCHIVED → MIME/size/signature → SHA-256 → `MinIO PutObject` → ثبت فوری callback مشروط حذف در rollback (و جبران در خطای احتمالی PutObject) → `case_document.saveAndFlush()` → Audit هم‌تراکنش. در Commit موفق، شیء نگه‌داری می‌شود.

**List**: بررسی DOCUMENT_READ + `CaseAccessService.requireRead` → query محدود به همان پرونده. **Download by ID**: بررسی DOCUMENT_READ → موجودیت → بررسی پرونده و REAL-scope/ACL → `requireOwnedObjectKey` → MinIO → دانلود attachment با UTF-8 و `nosniff/no-store`.

در تمام این مسیرها تست‌کننده برنامه `ROLE_TESTER` به شیء REAL دسترسی ندارد. این کنترل *لایه Application* جایگزین RBAC در Spring Security یا سیاست Bucket/RLS نیست.

## قرارداد و سازگاری

- مسیرهای HTTP موجود حفظ شده‌اند: `GET/POST /api/v1/cases/{caseId}/documents` و `GET /api/v1/documents/{documentId}/content`.
- شکل `DocumentResponse` و `case_document` تغییر نکرده؛ `sha256` و شناسه‌های قبلی برای provenance اولیه حفظ می‌شوند.
- حداکثر حجم فایل: ۲۵ MiB؛ انواع مجاز PDF، PNG، JPEG، WEBP، TXT، DOCX و XLSX.
- فایل‌های `.doc`, `.xls`, `.zip` در UI حذف شده‌اند چون Backend قبلاً آن‌ها را قبول نمی‌کرد.
- فایل DOCX/XLSX باید بخش‌های مشخص Office را داشته باشد. برخی فایل‌های نامعتبر قبلاً با سرآیند ZIP پذیرش می‌شدند و اکنون رد می‌شوند.
- فایل‌های تستی نباید به REAL bucket، ایمیل واقعی یا سرویس خارجی متصل شوند.
- مقدار `uploadedBy` اکنون باید با principal سرور یکسان باشد؛ جلوگیری از جعل انتساب سند.

## امنیت، داده‌های TEST/REAL و اعتبار حقوقی

1. با هدف Fail Closed، قابلیت بارگذاری در REAL برای `ROLE_TESTER` و `data_scope=TEST` بسته باقی می‌ماند. در این فاز هیچ «مسیر جدید TEST در MinIO» ایجاد نشده است.
2. قفل پرونده و استفاده از تراکنش برای عملیات نوشتنی به معنای اثبات RLS یا race واقعی PostgreSQL نیست؛ تست integration نیازمند DB ایزوله است.
3. حذف شیء یتیم در callback **best effort** است. در قطعی MinIO، آبجکت ممکن است باقی بماند؛ حذف دستی یا background sweep بدون سیاست بررسی و تأیید نشده است. callback فقط شیء **جدید همین درخواست** را حذف می‌کند، نه آبجکت‌های دیگران.
4. بررسی امضای فایل و ساختار ZIP **جایگزین اسکن بدافزار کامل** نیست. ClamAV، سیاست retention، signed URL، سیاست bucket و encryption-at-rest در این فاز فعال/تأیید نشده‌اند.
5. `Audit` گواه رضایت حقوقی، اعتبار سند یا امضای قانونی نیست. اتصال به knowledge ingestion نیازمند تعریف provenance (sourceId, docId, digest, version, reviewer) و تصمیم مستقل دامنه است.
6. به‌جای ثبت نام فایل یا داده شخصی در Audit، payload محدود `source=document` و `scope=REAL` ثبت می‌شود.

## فایل‌های تحویلی

- اصلاح `DocumentService.java`, `DocumentController.java`, `Phase03DocumentSafety.java`.
- اصلاح `DocumentsPanel.tsx`.
- افزودن `Phase11DocumentServiceTest.java`, `Phase11OfficeValidationTest.java`, `Phase11DocumentControllerTest.java`.
- این سند `docs/phase11-document/IMPLEMENTATION_FA.md`.

## تست‌ها و شواهد

- تست‌های JUnit/Mockito آماده شده‌اند برای: permission، TESTER، ACL، case scope، spoofed uploader، نبود transaction، حذف پس از rollback، عدم حذف پس از commit، شکست DB، شکست MinIO، key tampering، ZIP Office و هدر دانلود.
- **شواهد اجراشده در محیط ساخت**: تست مستقل Java 21 روی تابع validation/office/object key با Stub کوچک MultipartFile واقعاً اجرا و `PASSED: 9 assertions` ثبت شد؛ TSX نیز با Parser نصب‌شده TypeScript بدون خطای syntax بررسی شد. این‌ها جایگزین Maven، Mockito، MockMvc یا مرورگر نیستند.
- **فقط ایجاد تست ≠ اجرای موفق آن**. Maven روی محیط تولید بسته موجود نبوده و دسترسی به سورس کامل/Dependencyهای پروژه از داخل کانتینر وجود ندارد؛ آزمون‌های Maven و Spring HTTP در زمان ساخت بسته **NOT_RUN/BLOCKED** هستند.
- اجرای Maven بر روی سیستم کاربر با `Verify` و **لیست سفید چهار کلاس تست Mock/Unit/HTTP** انجام می‌شود. کلاس‌های تست DB/Integration دیگر در reactor اجرا نمی‌شوند (`-Dtest=... -Dsurefire.failIfNoSpecifiedTests=false`). هیچ Flyway/DB/Docker در نصب‌کننده اجرا نمی‌شود؛ اگر ساختن reactor به پیش‌نیاز دیگری برخورد کند، نتیجه BLOCKED است.
- Browser E2E، PostgreSQL/RLS و MinIO واقعی **NOT_VERIFIED** هستند؛ تنها با محیط Development ایزوله و داده TEST قابل انجام‌اند.
- وضعیت امنیت و امکان Rollback در ویندوز باید به‌صورت واقعی با `SelfTest/Audit/DryRun/ApplySource/Verify/Rollback` کنترل شود.

## نصب بدون استخراج دستی

فایل ZIP و `Apply-SakhtYar-Document-Phase11.ps1` را در Downloads قرار بده و در Windows PowerShell 5.1 اجرا کن:

```powershell
$script = "$env:USERPROFILE\Downloads\Apply-SakhtYar-Document-Phase11.ps1"
powershell -NoProfile -ExecutionPolicy Bypass -File $script -Mode SelfTest
powershell -NoProfile -ExecutionPolicy Bypass -File $script -Mode Audit
powershell -NoProfile -ExecutionPolicy Bypass -File $script -Mode DryRun
powershell -NoProfile -ExecutionPolicy Bypass -File $script -Mode ApplySource
powershell -NoProfile -ExecutionPolicy Bypass -File $script -Mode Verify
```

**کنترل قبل از اعمال**: SHA256 ZIP، فهرست تمام Entries، Manifest و SHA256 تمام payloadها، Branch و HEAD، وضعیت Dirty فایل‌های هدف و Git blob SHA. اختلاف غیرمنتظره باعث توقف بدون overwrite می‌شود. تغییرات نامرتبط کاربر حفظ می‌شوند. PowerShell 5.1 هیچ وابستگی به Python ندارد.

**Backup**: زیر `D:\ChatGPT_Projects\SakhtYar\SakhtYar-PatchBackups\Document-Phase11-...`. مسیر دقیق هنگام Apply چاپ می‌شود. State.json و نسخه اصلی هر فایل جایگزین‌شده ذخیره می‌شود.

**Rollback**: فقط با شرط عدم ویرایش فایل‌های هدف بعد از Apply؛ هیچ `git reset/clean/force` اجرا نمی‌شود.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File $script -Mode Rollback -BackupDir 'D:\ChatGPT_Projects\SakhtYar\SakhtYar-PatchBackups\Document-Phase11-<نام واقعی پوشه بکاپ>'
```

در صورت ندادن `-BackupDir` جدیدترین بکاپ فاز ۱۱ انتخاب می‌شود، اما ذکر مسیر چاپ‌شده مطمئن‌تر است. مقدار `-BackupDir` اختیاری است و هرگز به یک فراخوان داخلی با مقدار خالی تحمیل نمی‌شود.

## Acceptance Gate و Handoff

وضعیت تحویل: **READY_TO_VERIFY**، نه `E2E_VERIFIED`. تست‌های `mvn`، Frontend build و Windows PowerShell روی سیستم کاربر باید اجرا و Log ثبت شوند. تا زمان اثبات، این فاز نهایی/Production-ready اعلام نمی‌شود.

برای فاز بعد: ارزیابی linkage اسناد به knowledge source با provenance، اسکن بدافزار و retention، health policy برای bucket و Storage failure، طراحی isolated TEST objects، تست واقعی DB/PostGIS/RLS و concurrency و تست E2E با مرورگر.
