# فاز دوم E2E — ماتریس شکاف و تحویل ایمن

مبنای مطالعه GitHub `HAminzadeh/SakhtYar`, شاخه `v2`، HEAD بررسی‌شده: `666fbb523a87520a40141c2468da1f8684e4ff66`.

## حدود واقعی این تحویل

این بسته **۱۳ قرارداد سناریومحور آفلاین + ۱۳ Test Plan صریحِ Fixture/Negative/Expected/Evidence + ۱۳ Adapter ثبت‌شونده در Registry + تست‌های JUnit و Smoke + CI** اضافه می‌کند. اجرای این ۱۳ Adapter به `BLOCKED` ختم می‌شود، نه `PASSED`، چون هیچ گردش‌کار واقعی HTTP/DB/Browser و شواهد Artifact آن ایجاد نشده است. این تحویل از نظر Definition of Done پرامپت ۰۲ **ناقص** است و ادعای تکمیل ۱۳ Business Executor ندارد.

| ID | حوزه | آزمون آفلاین جدید | قبل از بسته | پس از بسته | مانع پذیرش واقعی |
|---|---|---|---|---|---|
| E2E-07 | Knowledge ZIP | ZIP مصنوعی و کنترل مسیر/Manifest | Diagnostic only | Offline contract + registry | Import واقعی، ACL، DB و Browser |
| E2E-08 | Pipeline | Checkpoint و Idempotency | Diagnostic only | Offline contract + registry | Worker ایزوله، API و Replay |
| E2E-09 | OCR | Page/Citation synthetic | Diagnostic only | Offline contract + registry | PDF/OCR واقعی با سند TEST |
| E2E-10 | Review | Reviewer ACL و Version | Diagnostic only | Offline contract + registry | تأیید/رد واقعی با Event History |
| E2E-11 | Governance | Evidence gate و rollback precondition | Diagnostic only | Offline contract + registry | انتشار ایزوله و Audit |
| E2E-12 | Retrieval | Workspace-scope synthetic filter | Diagnostic only | Offline contract + registry | Query واقعی/ایندکس و Citation |
| E2E-13 | Crawler | Deny SSRF در قرارداد آفلاین | Diagnostic only | Offline contract + registry | HTTP Client تزریقی و Redirect guard |
| E2E-15 | Builders | Matching و ACL مصنوعی | Diagnostic only | Offline contract + registry | Offer و Invite API واقعی |
| E2E-16 | Materials | Median/Outlier/Waste golden | Diagnostic only | Offline contract + registry | Cost snapshot و قیمت TEST |
| E2E-17 | Construction | Snapshot hash و Scoped access | Diagnostic only | Offline contract + registry | Calculate API و Persistence |
| E2E-18 | Feasibility | FAR synthetic golden | Diagnostic only | Offline contract + registry | Rule version و Source lineage |
| E2E-19 | Finance | Decimal 60/40 conservation | Diagnostic only | Offline contract + registry | Finance API و immutable snapshot |
| E2E-20 | Sensitivity | Shock/Replay deterministic | Diagnostic only | Offline contract + registry | Frozen clock و persisted lineage |

**آمار:** ۱۳/۱۳ قرارداد آفلاین دارای پیاده‌سازی، ۱۳/۱۳ Adapter ثبت‌شونده، **۰/۱۳ پذیرش Business E2E**. اعداد Diagnostic 97/144 و 0/24 مربوط به گزارش مستقل Phase07 و معادل این اعداد نیست.

## موارد به‌عمد غیرفعال

- هیچ HTTP واقعی، SQL، DDL، Seed، Migration، Role provisioning، RLS change، Docker، Restore، Cleanup یا تماس با providerهای خارجی در کدهای تحویلی وجود ندارد.
- `Phase2OfflineScenarioExecutor` دارای `executionType=OFFLINE_CONTRACT_ONLY` است. `ScenarioExecutorRegistry` صرفاً برای `FULL_BUSINESS_E2E` همراه با شواهد موردنیاز اجازه PASS می‌دهد.
- `Phase08SafetyAdmission` همچنان fail-closed است؛ فعال‌سازی Business Write منوط به شواهد مستقل فاز اول است.
- جست‌وجوی ایمن در `safeCrawlerTarget` فقط قرارداد آفلاین رد درخواست‌هاست، نه SSRF guard قابل استفاده برای شبکه حقیقی.
- مقادیر مالی/هزینه و اسناد این بسته داده‌های synthetic در حافظه‌اند و تست Production Service را جایگزین نمی‌کنند.

## گام‌های ضروری بعدی برای Definition of Done

۱. تأیید هفت Scenario فاز اول؛ فیزیک نقش محدود TEST و Forced RLS، positive/negative control و Egress containment را مستقل اثبات کنید. ۲. برای هر سناریو Adapter واقعی Service/API و Test fixture با Scope ثبت‌شده پیاده کنید. ۳. HTTP مثبت و منفی، JDBC روی principal محدود، Browser و Artifact Store SHA256/attestation اضافه کنید. ۴. Cleanup مبتنی بر Ownership را تست کنید. ۵. فقط پس از همه این شواهد، executionType را سناریو به سناریو ارتقا دهید؛ هیچ ارتقای گروهی یا toggle مجازی مجاز نیست.

## CI و اجرای محلی

تست‌های Source-only با Java 21، بدون Database و Network اجرا می‌شوند. تست‌های JUnit داخل ماژول `sakhtyar-quality` پس از نصب، با Maven reactor قابل اجرا هستند؛ CI جدید فقط تست‌های Pure Java را اجرا می‌کند و **پذیرش Business E2E نیست**.

## Handoff فاز سوم

E2E-21..24 (هوش مصنوعی، مرورگر/BFF، مانیتورینگ/اعلان، تنظیمات/بازگشت) همچنان به ایزولاسیون و شواهد معتبر وابسته‌اند؛ هیچ‌یک با این بسته آماده پذیرش شناخته نمی‌شوند. Dependencyها و کاتالوگ ۲۴تایی دست‌نخورده می‌مانند.

## سازگاری با آخرین Push

کامیت جدید شامل ۸ فایل Phase10 است. این بسته فقط فایل‌های تازه فاز دوم را ایجاد می‌کند، هیچ فایل Phase10 را تغییر نمی‌دهد و نباید فاز اول یا Source Safety را دور بزند.
