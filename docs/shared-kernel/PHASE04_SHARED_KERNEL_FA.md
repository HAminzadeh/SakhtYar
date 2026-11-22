# فاز ۰۴/۴۰ — ممیزی و سخت‌سازی Shared Kernel

**GitHub baseline:** `8fb8b5ad6fa1c7ec1cbb64b478914473378bf8d0` — 2026-11-22 05:39:42 UTC، شاخه `v2`.

## معماری و محدوده

- ماژول هدف: `backend/modules/shared-kernel`؛ پیکربندی وابستگی‌ها از `backend/pom.xml` و POMهای ۳۲ زیرماژول دیگر خوانده می‌شود.
- مسیر فراخوانی: `API Controllers -> Shared ApiExceptionHandler`، و `Agents/Domain -> CanonicalAgentRequest/Response`؛ XML از قرارداد XSD مشترک استفاده می‌کند.
- وابستگی‌ها از Shared Kernel به هیچ ماژول تجاری نباید وارد شود؛ این شرط در اسکریپت DAG بررسی می‌شود.
- PostgreSQL با داده‌های مشترک REAL/TEST تغییری نمی‌کند؛ هیچ Role، RLS، Migration یا Fixture ایجاد/ویرایش نمی‌شود.

## ماتریس نیازمندی و شواهد

| Path/Capability | Evidence (HEAD) | Gap | Priority | Action | Verification |
|---|---|---|---|---|---|
| `contract/CanonicalAgentResponse.java`, confidence | `Math.max(0, Math.min(1, confidence))` | `NaN` از محدودسازی عبور می‌کند، JSON معتبر نیست | P1 | رد کردن تمام non-finiteها؛ حفظ clamp مقادیر finite | `Phase04CanonicalContractsTest` |
| `xml/XmlContractValidator.java`, XXE/XSD | تنظیمات `ACCESS_EXTERNAL_*` با `catch ignored` | پشتیبانی‌نشدن محدودیت‌های خارجی باعث ابهام امنیتی می‌شود | P0 | سخت‌گیری fail-closed، رد DTD و محدودیت اندازه | `Phase04XmlSecurityTest` |
| `api/ApiExceptionHandler.java`, errors | پیام خام `IllegalArgumentException` به کلاینت بازمی‌گردد | احتمال افشای مقدار محرمانه داخلی | P1 | پیام عمومی، کد ثابت و `fieldErrors` افزایشی | `Phase04ApiErrorContractTest` |
| `contracts/agent-*-v2.schema.json` | JSON Schema 2020-12 با `schemaVersion=2.0` | regression و دادهٔ فارسی بدون تست اختصاصی | P1 | تست serialization، تاریخ، BigDecimal، ZWNJ | `Phase04CanonicalContractsTest` + source audit |
| `backend/pom.xml` + 33 module POM | Parent/BOM با نسخه 0.2.0-SNAPSHOT | کنترل چرخه و version drift باید خودکار باشد | P1 | ممیزی گراف read-only | `phase04_shared_kernel_contract_audit.py` |
| time/money/location shared types | DTOهای دامنه‌ای `BigDecimal`, `Instant`, UUID؛ currency در globalization | تضاد قطعیِ نیازمند نوع جدید اثبات نشده | P2 | بدون ایجاد نوع جدید و تغییر ناگهانی API | قراردادهای قبلی و Build کامل |
| JSON Schema/XSD مستند | versionهای `2.0` و `1.0` | schema migration لازم نیست | P2 | نسخه‌ها دست‌نخورده | source audit |

## سازگاری

- امضای constructorهای `CanonicalAgentRequest/Response` تغییر نکرده است. مقدار finite خارج [0,1] مانند قبل clamp می‌شود؛ تنها NaN و Inf که JSON ناسالم تولید می‌کردند رد می‌شوند.
- شکل سابق `timestamp,status,message,details` در `ApiError` حفظ می‌شود و دو فیلد **افزایشی** `code` و `fieldErrors` افزوده می‌شود. Constructor چهارتایی Java همچنان وجود دارد.
- پیام خام مربوط به `IllegalArgumentException` عمداً دیگر به کلاینت فرستاده نمی‌شود؛ این یک تغییر رفتاری امنیتی است و هر مصرف‌کننده متکی بر متن خام باید بررسی شود. دلیل `ResponseStatusException` برای خطاهای 4xx مطابق رفتار پیشین حفظ می‌شود؛ خطاهای 5xx دیگر دلیل خام را افشا نمی‌کنند.
- Schemaهای `agent-request-v2` و `agent-response-v2`، XSD و Flyway V1..V66 دست‌نخورده‌اند.
- وابستگی جدید Maven اضافه نشده است.

## امنیت و تهدیدها

- **XXE:** payloadهای `DOCTYPE` و `ENTITY` قبل از ایجاد parser رد می‌شوند؛ JAXP باید از feature/propertyهای منع شبکه و external schema واقعاً پشتیبانی کند.
- **XML DoS:** حداکثر اندازه متن ورودی ۱٬۰۴۸٬۵۷۶ کاراکتر UTF-16 است. عمق ساختار XML تنها تحت محدودیت‌های استاندارد JAXP قرار می‌گیرد، پس آزمایش‌های memory/performance با workload واقعی هنوز لازم است.
- **error leakage:** پیام‌های خام exception داخلی و خطاهای 5xx به کلاینت منتقل نمی‌شوند؛ پیام‌های 4xx دامنه‌محور هنوز باید از نظر PII در module مالک audit شوند.
- **finite JSON:** Double NaN/Infinity رد می‌شود و serialization تست می‌شود.
- **وابستگی چرخه‌ای و نسخه‌ها:** DAG ماژول‌ها بررسی می‌شود؛ هیچ تغییر خودکاری در BOM صورت نمی‌گیرد.

## verification و گیت‌ها

- Python offline audit روی سورس خواندنی، negative tests و Javac smoke بدون Spring در بسته تعبیه شده‌اند.
- `Verify` روی ویندوز در ماژول مشترک Java tests را با `mvn.cmd` اجرا می‌کند؛ عدم وجود Maven با وضعیت BLOCKED بازگردانده می‌شود. بررسی Git whitespace فقط روی فایل‌های **این فاز** اجرا می‌شود تا اختلاف فاز ۰۳ به صورت کاذب نتیجه را خراب نکند.
- این فاز بدون DB/network اجرا می‌شود؛ HTTP integration، بار و JVM‌های متفاوت و negative schema tests خارج از Maven، تنها وقتی واقعاً اجرا شوند PASSED خواهند شد.
- rollback فقط پس از تطبیق SHA هر فایل و با `-BackupDir` مجاز است؛ بدون git reset/clean یا force push.

## مرحله بعد

1. اجرای `Audit`/`DryRun`، سپس `ApplySource` و `Verify`؛ خروجی‌ها و لاگ‌های ویندوز مبنای پذیرش‌اند.
2. اجرای Maven کامل ۳۳ ماژول و npm build در CI؛ فقط Maven محدود این فاز در Verify به صورت خودکار اجرا می‌شود.
3. بررسی جداگانه هر خطای `git diff --check` باقی‌مانده از فاز ۰۳ قبل از Commit.
4. انتقال به فاز ۰۵، پس از Commit و Push تأییدشده؛ بدون ادعای موفقیت E2E یا اثبات سراسری TEST/REAL.
