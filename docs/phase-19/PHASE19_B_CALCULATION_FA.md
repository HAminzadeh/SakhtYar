# زیرفاز 19-B — موتور آنالیز بهای عملیات ساختمانی

Baseline: `v2 / 129ffff35339ebac9f1bff014d3f8777409da715` (2026-11-28 17:36:36 UTC). Migration جدید `V73__phase19_calculation_governance.sql`، الگوریتم محاسبه و قیمت 2.0 بدون تغییر. نام فنی Assembly و عنوان فارسی «آنالیز بهای عملیات ساختمانی» حفظ شده‌اند. وضعیت: READY_TO_VERIFY.

## ممیزی 19-A
- 19 فایل ثبت‌شده، V72 و Route فارسی تأیید شدند. هفت Workflow همان SHA موفق‌اند؛ CI شامل Backend، Frontend، golden/compose و اجرای تمام Migrationهای فعلی روی PostgreSQL موقت است. لینک CI: https://github.com/HAminzadeh/SakhtYar/actions/runs/46171391188 . این شاهد پذیرش کامل DB واقعی یا Business E2E نیست.
- V71 مجموع خطوط و مقدار مؤثر را کنترل می‌کند اما هزینه واحد سربرگ را مستقلاً با مبلغ کل و مقدار درخواست تطبیق نمی‌دهد. V73 این کنترل را قبل از INSERT برقرار می‌کند.
- V72 از Seal تعویقی برای تعریف استفاده می‌کند. پس از `SET CONSTRAINTS ALL IMMEDIATE` امکان تغییر بعدی تعریف در همان تراکنش وجود داشت (خود SQL آزمایشی 19-A این مسیر را استفاده می‌کرد). V73 تغییر مؤثر تعریف را پس از ثبت برآورد همان تراکنش رد می‌کند؛ تغییر در تراکنش‌های آینده مجاز و تاریخچه محفوظ است. تست قبلی برای هر دو نسخه سازگار شد. این تحلیل سورس است، نه مشاهده خرابی داده در DB کاربر.
- هیچ Migration قبلی تغییر نمی‌کند. هیچ داده قدیمی backfill یا بازنویسی نمی‌شود. V72 archive و V71 evidence و سیاست‌های RLS باقی می‌مانند.

## قرارداد عددی
`q_effective = round(q_base * (1 + waste) * q_requested, 6, HALF_UP)`؛ `line = round(q_effective * selected_unit_price, 2, HALF_UP)`؛ `total = sum(rounded_lines)`؛ `unit_cost = round(total / q_requested, 2, HALF_UP)`.
مقدارها positive `numeric(20,6)`، پول positive `numeric(20,2)`، پرت 0 تا 10 با حداکثر 6 رقم اعشار مؤثر. حد 10 همان قرارداد قبلی (1000%، نه 10%) است. اعشار اضافی مؤثر، نماهای غیرعادی، صفر، منفی، سرریز نتیجه و مبلغی که با گرد کردن صفر می‌شود رد می‌شوند. هزینه یا قیمت فرضی، تبدیل ارز خاموش یا تخفیف فرضی وجود ندارد. `MIN/MAX/AVERAGE/MEDIAN` با کنترل سازگاری آمار و انتخاب دقیق حفظ می‌شوند. تمام محاسبات Java BigDecimal هستند. مقایسه Decimal را به صورت رشته می‌دهد تا JS دقت را از دست ندهد. DTOهای موجود دست‌نخورده‌اند.

مثال مستقل مصنوعی: 100 واحد، جزء اول 12 × 1.05 × 100 × 4000 = 5,040,000؛ جزء دوم 8 × 1.03 × 100 × 5000 = 4,120,000؛ مجموع 9,160,000 و هزینه واحد 91,600. داده بازار واقعی نیست.

## API و رفتار
| Method | Path (prefix `/api/v1/assemblies`) | رفتار |
|---|---|---|
| POST | `/{id}/calculate` | همان JSON قبلی؛ `Idempotency-Key: UUID` اختیاری |
| POST | `/estimates/{id}/recalculate` | برآورد جدید بر اساس مقدار، نوع قیمت، ارز و منطقه برآورد قبلی و تعریف و قیمت معتبر جاری؛ کلید اختیاری |
| GET | `/estimates/{id}/compare?otherId={UUID}` | مقایسه دو نتیجه ثبت‌شده، نه محاسبه زنده؛ اختلاف after-before |
| GET | `/estimates/{id}/lineage` | estimateId، recalculatedFromId و algorithmVersion |
| GET | `/estimates/{id}/definition` | API فاز 19-A؛ Snapshot دقیق تعریف یا unavailable برای legacy |

کلید اختیاری UUID به `(assembly_id, calculated_by, request_key)` محدود است؛ در درخواست همسان همان ID برمی‌گردد، بدون محاسبه، ذخیره، Audit یا قیمت جدید. payload متفاوت یا تغییر منشأ با همان کلید خطا است. کلید تازه = قصد محاسبه تازه؛ کلید غایب رفتار قبلی و تولید برآورد جدید دارد. کلید به principal احراز هویت‌شده فعلی مربوط است؛ شناسه username طبق قرارداد actor فعلی و نه UUID حساب است. سناریوهای داخلی به همان API Java سه پارامتری و بدون کلید ادامه می‌دهند؛ Idempotency خود Scenario خارج از این زیرفاز است. خطاهای نگاشت HTTP از handler فعلی استفاده می‌کنند (تضمین 409 جدید ارائه نشده).
مقایسه فقط در یک assembly، ارز، واحد خروجی، نوع قیمت و منطقه دقیق انجام می‌شود. مقدار درخواست و نسخه الگوریتم هر دو در نتیجه دیده می‌شوند؛ تفاوت الگوریتم یا مقدار به معنی مقایسه صرفاً نرخ نیست. تغییر، اضافه، حذف و ثابت بودن خطوط با شناسه component مشخص می‌شود. Delta شامل مقدار، قیمت و ترکیب است؛ تجزیه اثر مستقل قیمت/مقدار در این بسته انجام نشده. رکوردهای قدیمی نامعتبر یا خطوط تکراری به‌زور اصلاح نمی‌شوند. lineage با FK و تطبیق scope و context ذخیره می‌شود. Recalculate برای snapshot legacy با context ناسازگار می‌تواند fail closed شود؛ backfill ندارد.

## حاکمیت نرخ و امنیت
استفاده مجدد از MaterialPriceResolver و query واجد شرایط فاز 18؛ برای هر ماده متمایز یک بار query در هر محاسبه Assembly. timestamp مشترک پس از قفل‌ها؛ DB V71 نیز اعتبار شواهد در زمان INSERT را مستقلاً می‌سنجد. `ScenarioService` ابتدا همه اسمبلی‌ها و سپس مصالح را با ترتیب UUID قفل می‌کند؛ کاهش احتمال deadlock، نه تضمین نبود deadlock برای همه workloadها. هیچ retry خودکار تراکنش یا تماس خارجی اضافه نشده. تست فشار/رقابت واقعی لازم است.
RBAC فعلی GET: CASE_READ یا USER_MANAGE، mutation: USER_MANAGE. TESTER بدون مجوز مستقیم تجاری، RLS و نقش فیزیکی همچنان مرز مستقل‌اند. کلیدها در cost_estimate scoped قرار دارند؛ V73 جدول جدید یا grant ندارد. Runtime policy 73 به policy قبلی افزوده می‌شود و باید طبق چرخه role/migration بررسی شود. V73 از SECURITY INVOKER و constraints استفاده می‌کند.

## دامنه تکمیلی و عدم ادعای تکمیل فراتر از قرارداد
- مالیات، دستمزد، تجهیزات، حمل، سربار و هزینه‌های غیرمستقیم: قرارداد مستقل در مدل فعلی Assembly صرفاً مبتنی بر Material وجود ندارد؛ نرخ/فرمول فرضی اضافه نشده. توسعه منابع مستقل و فهرست‌بها نیازمند تصمیم دامنه است. مالیات این فاز صرفاً تحلیل شده و قابل محاسبه جدید نیست.
- حذف فیزیکی جزء دارای برآورد همچنان ممنوع است؛ تغییر تعریف یا اسمبلی جدید. Soft-delete جزء نیازمند اصلاح همزمان مدل V71 count/line seal است و در این بسته پیاده نشده.
- UI کامل فرم محاسبه، تاریخچه و مقایسه در 19-C؛ صفحه فارسی 19-A حفظ شده. هیچ تغییر UI در 19-B نیست.
- تست‌های DB فیزیکی و Browser/Business E2E، benchmark و تست همزمانی چند connection هنوز اجرا نشده‌اند.

## Acceptance و مسیر تأیید
1. Source installer: SelfTest → Audit → DryRun → ApplySource → Verify با Maven آفلاین و Frontend build. گزارش Surefire تازه برای 8 کلاس اجباری؛ CI باید بعد از push دوباره اجرا شود.
2. قبل از migration، سرویس‌های متصل را مطابق رویه پروژه کنترل کن. `scripts/database/Invoke-SakhtYar-DatabaseMigration.ps1` و `Invoke-SakhtYar-DatabaseRoleLifecycle.ps1` را با حالت‌های Audit/clone و approval واقعی همان نسخه استفاده کن؛ پارامترها را از سورس همان checkout بخوان. هیچ migration یا grant خودکار از Installer انجام نمی‌شود.
3. روی DB clone disposable با V73 و role محدود provision شده: `psql -X -v workspace=<UUID> -f docs/phase-19/phase19_b_test_workspace_negative.sql`؛ متصل به LOGIN واقعی `sakhtyar_tws_<uuid without hyphens>` با workspace ACTIVE، نه SET ROLE و نه DB واقعی. SQL تراکنش را ROLLBACK می‌کند. `phase19_b_readonly_audit.sql` برای وضعیت catalog و نقش. تست 19-A منفی نیز مجدداً اجرا شود.
4. دو connection محدود روی clone: درخواست یکسان با کلید یکسان همزمان، فقط یک estimate؛ کلید یکسان با payload متفاوت خطا؛ سناریوهای هم‌زمان با ترتیب متضاد و ثبت خطا/زمان/lock؛ بررسی عدم ثبت ناقص. سپس retry با قیمت جدید باید ID قدیمی بدهد و کلید جدید باید برآورد جدید بدهد. مقایسه و lineage و HTTP 401/403/CSRF با نقش‌های واقعی تست شود. RLS بین workspaceها، TEST→REAL و immutability بررسی شود. هیچ PASS بدون اجرای واقعی.
5. Rollback سورس فقط قبل از اعمال V73 روی هر DB مصرف‌کننده این سورس و با Backup مشخص و سوییچ تأیید؛ پس از migration از rollback خودکار migration یا Flyway repair خودداری، forward fix یا بازیابی DB clone طبق رویه پروژه. هیچ rollback دیتابیس داخل بسته نیست.

## ماتریس 20 فعالیت
| ID | وضعیت سورس | Evidence/Action |
|---|---|---|
| 19-B-01 | موجود/سخت‌سازی | فرمول 2.0 و policy دقیق |
| 19-B-02 | تکمیل | q مؤثر با حدود ذخیره‌سازی |
| 19-B-03 | تکمیل | پرت 0..10، 6 رقم |
| 19-B-04 | حفظ قرارداد | مقدار سناریو به calculate منتقل می‌شود؛ ضریب اضافی جدید ندارد |
| 19-B-05 | حفظ/تست | چهار PriceBasis و آمار معتبر |
| 19-B-06 | reuse | MaterialPriceResolver و query فاز 18 |
| 19-B-07 | reuse/تست رگرسیون | اعتبار زمانی، بدون fallback |
| 19-B-08 | reuse | منطقه دقیق و city نیازمند province |
| 19-B-09 | reuse | ارز دقیق، بدون FX |
| 19-B-10 | تکمیل | هزینه خط و حدود مثبت |
| 19-B-11 | تکمیل | جمع خطوط کنترل‌شده و V71 seal |
| 19-B-12 | تکمیل | unitCost و V73 DB reconciliation |
| 19-B-13 | تصمیم دامنه | مالیات و سایر منابع بدون قرارداد جدید محاسبه نمی‌شوند |
| 19-B-14 | تکمیل | rounding و decimal policy |
| 19-B-15 | تکمیل | overflow و hostile exponent |
| 19-B-16 | تکمیل | حفظ V72 و guard پس از early seal |
| 19-B-17 | تکمیل API | recalculate، compare، lineage؛ UI در 19-C |
| 19-B-18 | تکمیل سورس | UUID idempotency، unique index، ordering؛ physical concurrency pending |
| 19-B-19 | جزئی | harness Java 45 assertions؛ JUnit/MVC و SQL آماده؛ benchmark واقعی pending |
| 19-B-20 | تکمیل بسته | manifest، SHA256، backup، rollback source و متن Commit |
