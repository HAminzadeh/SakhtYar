# Phase 04 | Gap Matrix قبل و بعد از Patch

مبنا: GitHub `v2` در `c2537a043d9f71ab92e28b3d8b69479ee3a62525`.

| نیاز | شواهد/وضعیت قبل | کمبود | اصلاح Patch | ریسک REAL | آزمون پذیرش و وضعیت |
|---|---|---|---|---|---|
| Import ZIP | safeUnzip و SHA فایل‌ها موجود | کپی بدون سقف بایت، Pathهای تکراری، CRC/Ratio بررسی جدا نداشت | `Phase04ArchiveGuard` و اتصال به Import | خطر Temp/Archive تخریبی | ۲۰ کنترل مصنوعی، JUnit در Windows **NOT_RUN**، Import زنده **BLOCKED** |
| Provenance | `dataset_version` و manifest موجود | منبع و ناشر بسته به‌صورت مستقل تأیید نشده | رد hash malformed و duplicates؛ قرارداد provenance Fake | نسبت‌دادن نادرست قوانین | Source-only؛ تأیید قانونی **BLOCKED** |
| OCR | OCR Review DB و PDF preview موجود | Fake قابل تکرار + SHA صفحه | `Phase04FakeOcrEngine` | اختلاط AI suggestion با متن منبع | `FAKE_ADAPTER_PASS`، OCR واقعی **NOT_RUN** |
| Checkpoint | جدول‌های V33/V36 و سرویس Resume موجود | Crash/retry واقعی/poison-job تست نشده | In-memory lifecycle contract + negative replay | احتمال تکرار نوشته | Synthetic PASS، Runtime **BLOCKED** |
| Human review | Rule Review DB Event و Lock موجود | کنترل صریح تعارض نسخه در workflow کامل | Synthetic optimistic version + separation | انتشار غیرمعتبر | Synthetic PASS، DB conflict **BLOCKED** |
| Retrieval | جستجو و Normalizer فارسی وجود دارد | نسخه و Workspace در همه backend endpoints اثبات نشده | Synthetic pinned version index | افشای داده | Synthetic PASS، Runtime RLS **BLOCKED** |
| Crawler allowlist | CrawlTargetPolicy/Fetcher موجود | UrlDiscovery بدون Host مقدار همه لینک‌ها را برمی‌گرداند | fail-closed و Canonicalizer syntax | SSRF | JUnit source اضافه، Windows **NOT_RUN** |
| Crawler authorization | Registry مسیر Crawler در SecurityConfig اختصاصی نبود | امکان Crawler برای سایر principalها | HTTP و service هر دو USER_MANAGE | real outbound | Source-only + JUnit؛ HTTP negative **NOT_RUN** |
| Robots | `respectRobots` در Entity موجود | در Ingestion خوانده نمی‌شد | robots.txt با پاسخ نامعتبر fail closed | تماس real مجاز فقط برای Admin | synthetic parser PASS؛ HTTP REAL/TEST safety **BLOCKED** |
| Response MIME | PageFetcher فقط حجم را محدود می‌کرد | MIME موفق نامعتبر مسدود نبود | allowlist PDF/text/HTML/JSON/XML | داده مخرب | JUnit Windows **NOT_RUN** |
| Data isolation | shared DB و RLS جدا در فازهای قبل | تأیید Principal و TEST storage ناقص | هیچ DB تغییری ایجاد نشده | بالا؛ REAL باید حفظ شود | **BLOCKED** |
| Business E2E | ۲۴ سناریو تعریف، ۲۳ ناقص و E2E-14 محدود | Executorهای تجاری کامل موجود نیستند | تغییری در status انجام نمی‌شود | PASS جعلی | ۰/۲۴ تجاری، **BLOCKED** |

تعداد Gateهای PASS فقط همان آزمون‌های اجراشده و مستند هستند. Build کل پروژه، تست یکپارچه، SQL live و Git CI مربوط به همین Patch هنوز در این محیط اجرا نشده‌اند.
