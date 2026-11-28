# فاز ۱۹-A — زیرساخت و امنیت آنالیز بهای عملیات ساختمانی

## مبنا و وضعیت تحویل
- مخزن: HAminzadeh/SakhtYar، شاخه v2، HEAD `4ca07c63b8307a0eacbe5636189b091aa13cd690`، تاریخ 2026-11-28T06:28:07Z.
- درخت بازگشتی GitHub: 3328 ورودی، بدون truncation؛ آخرین Migration: V71. ماژول اسمبلی 14 فایل Java اصلی و 5 فایل تست در مبنا دارد.
- پنج Commit اخیر شاخه: `4ca07c63` (18-C)، `8aa7d0a0` (CI inventory)، `cd2ed498` (18-B)، `bc47498e` (18-A)، `a2392904` (منو و گردش کار).
- سورس GitHub خوانده شد؛ اجرای Git clone در محیط تولید بسته به علت DNS ناموفق بود. هیچ تغییر یا Push از راه دور انجام نشده است.
- وضعیت انتشار: READY_TO_VERIFY / NOT RELEASED. اجرای کامل Maven، PostgreSQL، فیلتر امنیت HTTP و UI در محیط واقعی نیازمند پذیرش مستقل است.

## تصمیمات دامنه و سازگاری
- عنوان فارسی: **آنالیز بهای عملیات ساختمانی**؛ نام Assembly، نام جدول‌ها و مسیر API قبلی بدون تغییر.
- منطق نرخ، Resolver، نسخه الگوریتم محاسبه 2.0، قراردادهای موجود Estimate و Scenario حفظ می‌شوند. Version تعریف از نسخه الگوریتم مالی جداست.
- واحد خروجی پس از ایجاد ثابت است، حتی اگر برآوردی هنوز ثبت نشده باشد. برای واحد متفاوت، آنالیز بهای جدید با کد متفاوت تعریف شود. واحد‌های قدیمی ناشناخته بدون تغییر قابل نگهداری‌اند؛ واحد جدید از کاتالوگ MaterialFoundation اعتبارسنجی می‌شود.
- تغییر اجزا به صورت PUT با همان شناسه، با قفل والد و کنترل مصالح/گونه، واحد، مقدار، پرت، ترتیب و Note. حذف فیزیکی فقط برای جزء بدون برآورد تاریخی مجاز است. حذف والد ممنوع؛ غیرفعال‌سازی، آرشیو عملیاتی است. غیرفعال‌سازی در محاسبات جدید خطا می‌دهد، سوابق قبلی حفظ می‌شوند. قابلیت حذف منطقی اجزای دارای سابقه، به دلیل FK و seal V71 و اثر روی محاسبات، در این بسته اضافه نشده است؛ برای حذف از تعریف جدید، آنالیز بهای جدید ایجاد شود.
- Revision 1 برای وضعیت موجود در زمان نصب V72 است، نه بازسازی نسخه‌های پیشین. اولین تغییر، تعریف قبلی را در `cost_assembly_revision` ثبت می‌کند. هر تغییر واقعی در تعریف یا اجزا یک Revision جدید ایجاد می‌کند؛ یک عملیات چندردیفی می‌تواند چند نسخه ایجاد کند. تغییر صرف timestamp نسخه جدید ایجاد نمی‌کند.
- V72 تعریف برآورد جدید را توسط trigger، با قفل والد، در `cost_estimate.assembly_definition` و `definition_revision` ذخیره می‌کند؛ کنترل deferred اجازه تغییر تعریف در فاصله snapshot و seal را نمی‌دهد. V71 همچنان مسئول نرخ، خطوط، مبالغ و تاریخچه است. Estimateهای پیش از V72 تعریف کامل تاریخی ندارند: API `available=false`، بدون داده جعلی. مبالغ قبلی دست نخورده‌اند.
- `cost_assembly_revision` آرشیو append-only است؛ RLS FORCE و دو policy از همان قرارداد scope فاز ۱۸، نقش فیزیکی TEST، کنترل والد و snapshot دقیق. SECURITY INVOKER؛ هیچ GRANT/ALTER ROLE خودکار. DBA با superuser خارج از مدل تهدید runtime است. دسترسی EXECUTE توابع جدید و ACL جدول جدید باید در policy واقعی نقش عملیاتی بررسی شود. پاکسازی آرشیو TEST و سیاست retention در این بسته خودکار نشده‌اند؛ محدودیت موجود V71 حفظ می‌شود.
- API جدید از JdbcTemplate با همان datasource تراکنشی و RLS استفاده می‌کند؛ خواندن تعریف در تراکنش repeatable-read. تطابق اتصال فیزیکی در runtime هنوز باید آزموده شود.
- جست‌وجوی فارسی با MaterialFoundation.search. لیست قبلی بدون تغییر شکل پاسخ باقی می‌ماند؛ صفحه‌بندی تعریف‌های تاریخی (keyset، حداکثر 100) اضافه شده است. صفحه‌بندی کاتالوگ و بهینه‌سازی query بزرگ در 19-B/C.
- UI پایه مشاهده کاتالوگ، نسخه جاری، نسخه‌های آرشیو و اجزای آنها به گروه «بازار و همکاران» اضافه می‌شود. فرم‌ها و گردش محاسبه، مقایسه، خروجی گزارش، نمایش نام مصالح، برآوردها و شواهد کامل در 19-C؛ این صفحه هیچ داده مصنوعی یا محاسبه مالی ندارد.

## API
| Method | Path | Permission / Contract |
|---|---|---|
| PUT | /api/v1/assemblies/{id}/components/{componentId} | USER_MANAGE؛ همان CreateComponentRequest؛ UUID جزء ثابت |
| GET | /api/v1/assemblies/{id}/definition | CASE_READ یا USER_MANAGE؛ نسخه جاری |
| GET | /api/v1/assemblies/{id}/revisions?limit=20&beforeRevision=5 | همان مجوز؛ نسخه‌های آرشیو، نزولی و بدون جاری |
| GET | /api/v1/assemblies/{id}/revisions/{revision} | همان مجوز؛ جاری یا آرشیو |
| GET | /api/v1/assemblies/estimates/{id}/definition | همان مجوز؛ تعریف ثابت برآورد یا unavailable برای Legacy |

قرارداد Definition: `{revision: string|null, definitionJson: string|null, historical: boolean, available: boolean}`. JSON متن برای حفظ دقت اعداد؛ مقدار و ضریب اجزا در JSON داخلی به صورت decimal string. موارد ناموجود و خارج از scope با رفتار خطای یکسان سرویس. APIهای قبلی و DTOهای response قبلی تغییر نکرده‌اند. نقش TESTER مجوز مستقیم کسب‌وکار نمی‌گیرد. POST/PUT/DELETE تابع CSRF و RBAC فاز ۱۸ باقی می‌مانند.

## مسیر داده و تهدید
Material + Variant → Assembly Definition → Revision Archive → Estimate Definition + Estimate Lines (V71) → Scenario → Feasibility → Finance. Quality فقط fixture/executor/assertion/evidence.

تهدیدها: ID خارجی، cross-workspace، دستکاری snapshot، جعل نسخه، تغییر واحد و معنا، حذف وابستگی، اجرای همزمان. کنترل‌ها: قفل والد، FK و guard، archive immutable، RLS فیزیکی، RBAC و کنترل scope، تراکنش atomic. Deadlock در رقابت قفل‌های مستقیم DB ممکن است باعث abort شود؛ retry/idempotency و تست بار در 19-B. هیچ تنظیم نقشی برای عبور از RLS تغییر نکند.

## Gate پذیرش
1. اجرای Audit / DryRun / ApplySource / Verify و بررسی git diff؛ منبع تنها، بدون Migration خودکار.
2. اجرای Maven هدفمند (در بسته) و بررسی تعداد تست‌های Surefire؛ سپس build frontend. هر خطای ابزار یا تست = BLOCKED/FAIL، نه PASS.
3. پیش از V72، صحت V71 و وضعیت Flyway روی clone؛ از `scripts/database/Invoke-SakhtYar-DatabaseRoleLifecycle.ps1` و `Invoke-SakhtYar-DatabaseMigration.ps1` مطابق workflow موجود استفاده کنید. DraftClone برای inventory اشیای جدید و policy بررسی‌شده؛ تایید اپراتور الزامی. V72 نیازمند SELECT/INSERT آرشیو و عدم UPDATE/DELETE/TRUNCATE؛ توابع جدید و role فیزیکی TEST هم بررسی شوند. از تغییر Migrationهای پیشین یا اعطای privilege عمومی خودداری شود.
4. اجرای `phase19_a_readonly_audit.sql` با login عملیاتی روی clone، سپس `phase19_a_test_workspace_negative.sql` فقط با login فیزیکی TEST فعال روی clone و متغیر workspace. SQL fixture داده ساختگی، transaction rollback و بدون تماس خارجی دارد. بررسی REAL/TEST، آرشیو immutable، direct INSERT، unit/revision tamper، مرجع تاریخی و مرز workspace. نتیجه SQL باید واقعی ثبت شود.
5. HTTP روی محیط آزمایشی ایزوله: CASE_READ GET مجاز و PUT/POST/DELETE ممنوع؛ USER_MANAGE با CSRF مجاز؛ TESTER بدون مجوز تجاری ممنوع؛ درخواست بدون CSRF رد؛ شناسه از workspace دیگر قابل خواندن نباشد. فرم PUT با مقدار صفر، واحد ناسازگار، گونه غیرفعال، id خارجی و جزء دارای برآورد تست شود. کنترل رفتار error envelope موجود؛ هیچ کد HTTP دقیق برای خطاهای سرویس خارج از validation ادعا نشده است.
6. UI: با دو نقش مجاز/غیرمجاز، صفحه فارسی `/assemblies`، جست‌وجو، وضعیت، نسخه جاری/تاریخی و empty/error/loading را بررسی کنید. از داده REAL برای تست استفاده نشود.
7. اجرای regression فاز ۱۸ و زنجیره Business E2E با شواهد واقعی روی محیط تست، قبل از 19-B. V72 از V71 استفاده می‌کند و در صورت عدم پذیرش V71 قابل انتشار نیست.
8. هر گونه تغییر لوکال ناسازگار، mismatch SHA یا نبود محیط آزمون = BLOCKED؛ هیچ تست جعلی.

## ماتریس ۲۰ فعالیت
| شناسه | اقدام بسته | وضعیت پذیرش |
|---|---|---|
| A01 | HEAD، پنج commit، درخت | انجام ممیزی از GitHub |
| A02 | ممیزی Controller/Service/DTO/Entity/Repo/SQL/tests | انجام ممیزی مرتبط؛ build کامل pending |
| A03 | استفاده مجدد V71 و MaterialFoundation | runtime pending |
| A04 | مرزبندی Material/Assembly/Scenario/Quality | مستند |
| A05 | validation کد، نام، دسته، شرح، metadata | سورس آماده، Maven pending |
| A06 | غیرفعال‌سازی و ممنوعیت حذف والد | سورس آماده، DB pending |
| A07 | PUT جزء، حذف امن | سورس آماده، آزمون pending |
| A08 | V72 آرشیو نسخه و snapshot | سورس آماده، DB pending |
| A09 | sortOrder نامنفی و ترتیب قطعی | سورس آماده |
| A10 | کنترل material/variant | سورس آماده، تست pending |
| A11 | واحد کاتالوگ و واحد ثابت خروجی | سورس آماده |
| A12 | اعداد، متن، metadata، DTO | harness مستقل policy؛ Maven pending |
| A13 | FK، check، index و guard | SQL آماده، clone pending |
| A14 | V72، inventory guard، role policy | SQL آماده، clone pending |
| A15 | reuse RBAC، عدم تغییر نقش TESTER | HTTP pending |
| A16 | FORCE RLS و workspace parent binding | SQL آماده، physical login pending |
| A17 | least privilege، policy review | اپراتور و clone pending |
| A18 | Audit service موجود + رویداد component update؛ history مستقل در DB | تست pending |
| A19 | JUnit policy/service/MVC و SQL fixture | اجرای کامل pending |
| A20 | ZIP/Launcher/manifest/backup/rollback | تست synthetic installer؛ Windows pending |

## محدودیت‌های باقی‌مانده
عدم clone مستقیم در محیط تولید، Maven و PostgreSQL و Windows PowerShell 5.1 در آن محیط موجود نبودند؛ تأیید runtime، SQL syntax/behavior روی PostgreSQL، TypeScript typecheck و Vite کامل و E2E مسدودند. تست‌های standalone Java و syntax TSX و synthetic installer صرفاً شواهد محدود هستند. هیچ داده واقعی تغییر نکرده و هیچ release production پذیرفته نشده است.
