# فاز ۱۷-A — آماده‌سازی بانک قوانین و امنیت (SakhtYar)

**مبنای بررسی GitHub:** `v2@8936cbdbef08e2ee9f211b3ce29fe3f83f2f702d`، تاریخ ۲۰۲۶-۱۱-۲۵ ساعت 09:47 UTC. این SHA صرفاً مبنای بسته است؛ نصب‌کننده در سیستم کاربر مجدداً HEAD/Index/Workspace را کنترل می‌کند.

## مبنای داده و محدوده

آخرین گزارش فقط‌خواندنی ارسالی کاربر: ۵۲ بسته AI، ۱۸٬۴۴۱ آیتم، ۱۸٬۲۶۰ تأیید Staging، ۲٬۵۹۶ مورد LAW در کاتالوگ، شامل ۹۳۴ RULE و ۱٬۶۶۲ SEGMENT؛ فقط ۹ Rule متنی Canonical در حالت CANDIDATE، صفر Rule تأییدشده، صفر Release منتشرشده و صفر UrbanRule. **این آمار تاریخی است و با نصب این بسته تغییر نمی‌کند.**

## تغییرات محدود این زیرفاز

1. کنترل دسترسی در `RegulationService.evaluate/evaluations/evaluation`، قبل از خواندن Scenario و خروجی Evaluation؛ بررسی REAL بودن Property. `CASE_READ` عمومی معادل مالکیت پرونده نیست.
2. `RegulationEvidenceService` قبل از خواندن Evidence و MunicipalityInputs مجوز پرونده و Scope را می‌سنجد؛ کشور را از `JurisdictionService` دریافت می‌کند؛ جست‌وجوی Evidence را با فیلتر کشور، شهر، منطقه، اعتبار زمانی و Hash در SQL محدود می‌کند؛ نتیجه بیش از ۵۰۰۰ مورد به خطا تبدیل می‌شود، نه حذف خاموش.
3. کنترل `USER_MANAGE` در خود `RegulationEvidenceService.bind` و مسیرهای حساس `KnowledgeGovernanceService.review/publish`؛ Principal باید با شناسه بازبین مطابقت داشته باشد.
4. `KnowledgeFinalPipelineService` پیش از حذف خروجی نسخه جاری در استخراج مجدد، وجود بازبینی، انتشار و Legal Segment بررسی‌شده را کنترل می‌کند و در این وضعیت **پیش از حذف** متوقف می‌شود. توجه: Atomicity کل فرآیند طولانی در چند Instance هنوز نیازمند تست و طراحی مجزا است.
5. تکمیل Bean Validation درخواست Rule.
6. سرویس جدید `RegulationCandidateBridgeService` صرفاً با SELECT رکوردهای APPROVED + LAW + Published در کاتالوگ کارشناسی را صفحه‌بندی می‌کند و برایشان وضعیت Provenance و پیشنهاد عددی **غیرالزام‌آور** تولید می‌کند. ارتباط پیشنهادی با منبع اصلی تنها بر اساس تطابق واقعی SHA و صفحه ساخته می‌شود. تمام خروجی‌ها نیازمند بازبینی انسانی‌اند؛ هیچ DML روی قوانین یا Publication انجام نمی‌شود.
7. `RegulationCandidatePolicy`: نرمال‌سازی نویسه‌های عربی/فارسی، اعداد، استخراج بسیار محدود ضوابط عددی، شناسایی چندعدد و ابهام، Fingerprint پایدار با SHA-256. در متن چندمعنایی خروجی `REVIEW_REQUIRED` است.

## قرارداد API جدید

`GET /api/v1/knowledge/admin/phase17/bridge/candidates?page=1&pageSize=20&recordType=ALL`

- مجوز: `USER_MANAGE` در لایه SecurityConfig (قاعده موجود `/api/v1/knowledge/admin/**`) **و** در خود Service.
- ورودی `recordType`: `RULE | SEGMENT | ALL`؛ حداکثر ۱۰۰ نتیجه در صفحه.
- خروجی: `page,pageSize,total,items,publication,note`؛ در هر item مقدارهای قدیمی DB همراه `provenanceState`, `legalStatus=NOT_VERIFIED_NOT_PUBLISHED`, `reviewState=HUMAN_REVIEW_REQUIRED`, `fingerprint`, `suggestion`, `relationshipProposal`.
- پیشنهادهای وابستگی تبصره/بند `REVIEW_ARTICLE_DEPENDENCY` هستند؛ **هیچ رابطه حقوقی معتبر، گراف اجرایی یا UrbanRule از روی تشابه عنوان ایجاد نمی‌شود**.
- متن و Source در پاسخ فقط برای مدیر مجاز قابل مشاهده است؛ API مجوزشده مستقلی برای دریافت PDF اصل لازم خواهد بود.

## محدودیت‌های واقعی که نباید مخفی شوند

- **Scenario کاتالوگ مشترک است** و `ConstructionScenarioEntity` فعلاً `caseId` ندارد. در این بسته مالکیت Property/Case کنترل می‌شود، ولی تعلق مستقل Scenario به پرونده قابل اثبات نیست؛ طراحی ارتباط سراسری/پرونده‌ای و تست آن در فاز B/C لازم است. ادعای رفع کامل این ریسک ممنوع است.
- `knowledge_rule_review` در V45 برای APPROVED شهر غیرخالی الزام می‌کند؛ قوانین واقعاً سراسری بدون تغییر Constraint و تست Migration کلون قابل انتشار از مسیر موجود نیستند. جست‌وجوی جدید می‌تواند Null city را مصرف کند اما مسیر تأیید سراسری همچنان `BLOCKED` است.
- Source hash+page فقط تطابق فنی پیشنهاد را می‌دهد، نه اثبات اعتبار قانون؛ اصل PDF، متن و مرجع باید توسط کارشناس بررسی شود.
- Bridge در فاز A **فقط Preview خواندنی** است؛ صف بازبینی از داده Staging موجود استفاده می‌کند، به `knowledge_rule` Candidate نمی‌نویسد. ذخیره انتخاب‌های بازبین، رابطه قطعی مواد، ثبت و تأیید Canonical در فاز B نیازمند طراحی و مجوز مستقل است.
- آزمون PostgreSQL/RLS/مرورگر و کلیه آزمون‌های Maven در محیط ویندوز کاربر باید واقعاً اجرا شوند؛ روی این محیط Maven و Clone خصوصی در دسترس نیست.

## Threat model

| تهدید | کنترل/وضعیت |
|---|---|
| IDOR ارزیابی/شواهد | Service case access + REAL gate؛ تست منفی آماده |
| TESTER به داده REAL | `CaseAccessService` موجود، تقویت در Regulation؛ RLS DB جداگانه نیازمند آزمون |
| جعل قانون از AI | Bridge خواندنی، خروجی PROPOSED_ONLY، هیچ publish یا bind |
| Hash نادرست سند | فقط تطابق دقیق SHA پیشنهاد؛ بدون تطبیق، BLOCKED_MISSING_SOURCE |
| چند تطبیق سند | BLOCKED_AMBIGUOUS_SOURCE |
| تکرار محتوا | Fingerprint پایدار بدون حذف و ادغام خودکار |
| انتشار بدون مجوز | USER_MANAGE داخل Service و Principal واقعی |
| استخراج مجدد نسخه تأییدشده | Guard قبل از DELETE؛ تست DB و هم‌زمانی هنوز لازم |
| سوءاستفاده از LIMIT 1000 | فیلتر SQL پیش از bound؛ حد ایمن ۵۰۰۰ با خطای صریح |
| تغییرات محلی غیرمنتظره | نصب‌کننده fail-closed و Patch بر مبنای Git Blob |
| تزریق SQL | `JdbcTemplate` با bind parameter و whitelist نوع رکورد |

## سازگاری DB/API

- **بدون Migration جدید.** V15، V45، V50–V55، V68 تغییر نمی‌کنند.
- بدون حذف/ویرایش رکوردهای REAL/TEST؛ نصب‌کننده فقط فایل‌های سورس و تست را می‌نویسد.
- مسیرهای موجود `/api/v1/regulations/**` و ساختار DTO خروجی حفظ می‌شوند؛ در درخواست غیرمجاز ممکن است 404 برگشت داده شود.
- constructor سرویس‌های Regulation برای تزریق CaseAccess/Jurisdiction تغییر کرده؛ سازگاری bean wiring باید با Maven verify شود.
- UI اصلی در A تغییر نکرده و در فاز C اضافه می‌شود.

## Verify روی ویندوز

ZIP و اسکریپت PowerShell را در Downloads قرار دهید. ابتدا `SelfTest`,`Audit`,`DryRun`، سپس `ApplySource`,`Verify` را اجرا کنید. ZIP خودکار استخراج می‌شود؛ هیچ دستوری برای Flyway، پستگرس یا مدل خارجی در نصب‌کننده نیست. برای تست DB فقط SQL `READ_ONLY_AUDIT.sql` روی Clone جداگانه و با اطلاع کاربر اجرا شود.

**Gate:** `READY_TO_VERIFY` برای کد و تست‌های آماده؛ `BLOCKED` برای شواهد حقوقی واقعی، Scenario case-binding، پذیرش PostgreSQL/RLS و E2E. از متن کاتالوگی، تأیید حقوقی استنتاج نکنید.
