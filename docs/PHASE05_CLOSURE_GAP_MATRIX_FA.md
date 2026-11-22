# Phase 05 | Gap Matrix پیش / پس از Patch

> مبنا: شاخه `v2`، HEAD `831a54ec612166f825cc8fc87159d67d3b2b9247` (۸ نوامبر ۲۰۲۶). این جدول درباره «کد تحویلی» است؛ تأیید Runtime مستقل الزامی است.

| نیاز | وضعیت مشاهده‌شده در GitHub | کمبود | اصلاح تحویلی | ریسک REAL | پذیرش و وضعیت |
|---|---|---|---|---|---|
| Golden مالی IRR | `FinancialCalculator`, Golden v1 و تست conservation موجود | تخصیص باقیمانده وابسته به ترتیب ورودی بود | مرتب‌سازی **محاسبه** بر اساس درصد سهم، سپس کلید و حفظ ترتیب پاسخ + Oracle مستقل Decimal v2 | بدون DB | Python oracle + JUnit؛ `SOURCE_ONLY` تا اجرای Maven |
| Precision و Boundary | BigDecimal HALF_UP و tolerance=0.0001 موجود | ورودی null و دقت نامحدود | null check، حداکثر ۳۰ رقم صحیح و ۱۲ رقم اعشار، حداکثر 1000 مشارکت‌کننده | بدون DB | JUnit منفی و تست Python |
| مصالح و Assembly | متوسط/میانه، waste factor و lineTotal موجود | قیمت منفی candidate می‌توانست anomaly HIGH شود | رد candidate/history منفی + JUnit | بدون DB | Unit |
| FAR و sensitivity | FeasibilityCalculator/SensitivityCalculator و Golden v1 موجود | Golden الگوریتم مستقل و version pinning ناکامل | Golden v2 و تست‌های مستقل FAR، Waste، Shock | بدون DB | Python + JUnit |
| ACL Snapshot | `AnalysisSnapshotService` بدون CaseAccessService | IDOR خواندن، ثبت و Lineage | کنترل Service-level برای create/get/byCase/lineage و filter byScenario | محافظ روی REAL؛ بدون تغییر اطلاعات | Mockito positive/negative و HTTP runtime `NOT_RUN` |
| ACL Feasibility | `FeasibilityService` بدون CaseAccessService | IDOR و نوشتن برای پرونده دیگر | کنترل requireWrite/read و فیلتر سناریو | محافظ روی REAL؛ بدون تغییر اطلاعات | Mockito و HTTP runtime `NOT_RUN` |
| Immutable Snapshot | `SnapshotHasher` و Entity عمومی موجود | hash متادیتای frozen-clock + owner-scoped fixture proof به‌صورت تایپ‌دار نبود | `Phase05NumericSnapshot` عددی/immutable با SHA256 های input/output/envelope | صرفاً حافظه | Java21 Harness؛ دیتابیس `BLOCKED` |
| Fixture domain 15..20 | `Phase02FixtureFactories` fail-closed | seed/cleanup اجرایی با principal محدود نداریم | `Phase05SyntheticFixtureManifest`: UUIDهای run-scoped، state=BLOCKED و no-write guard | هیچ تغییری در TEST/REAL | JUnit؛ Integration `BLOCKED` |
| API و Handoff | Controllerهای واقعی وجود دارند | قرارداد تست/Fixtures و وابستگی‌ها کامل نبود | README + Handoff هر سناریو + endpointهای دقیق | صرفاً سند | Source review |
| Performance/Restart | snapshot persist و transactionهای دامنه موجود | Crash retry و collision زنده اثبات نشده | قرارداد و تست عدم تغییر هش بر اساس input+version؛ **بدون** ادعای DB idempotency | DB زنده ممنوع | `BLOCKED` |

## مواردی که در این بسته عمداً تغییر نمی‌کنند

- هیچ Migration، Role/Grant/RLS یا داده REAL تغییر نمی‌کند؛ Clone یا اجرای PostgreSQL وجود ندارد.
- هیچ `FULL_BUSINESS_E2E` جدید یا Fake PASS ساخته نمی‌شود. سناریوهای ۱۵ تا ۲۰ همچنان `BLOCKED` خواهند بود.
- `Phase05NumericSnapshot` جایگزین `AnalysisSnapshotService`/جدول‌های واقعی نیست و هیچ رکوردی ذخیره نمی‌کند.
- تراکنش‌های دامنه، Snapshot immutable در DB و محدودیت واقعی TEST/REAL فقط با Principal مستقل و تست Runtime قابل قبول هستند.
