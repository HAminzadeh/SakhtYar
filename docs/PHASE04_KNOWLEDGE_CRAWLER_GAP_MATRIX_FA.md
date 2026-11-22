# فاز چهارم - گزارش پیاده‌سازی محدود و ماتریس شکاف‌ها

مبنای GitHub: `v2` / `2a1bc5072c08a54f4c2e674b17cfc04405085494`، تاریخ 2026-11-04.
این بسته فقط اصلاح سخت‌سازی خزشگر، Golden Corpus مصنوعی، آزمون‌های Unit و ممیزی خواندنی دارد. **هیچ سناریوی Business E2E با این بسته PASS نمی‌شود.**

| سناریو | وضعیت پیاده‌سازی واقعی | BLOCKER برای FULL_BUSINESS_E2E |
|---|---|---|
| E2E-04 ضوابط | سرویس ارزیابی موجود + Golden arithmetic | نسخه‌بندی، انتشار و POST/DB و negative permission در TEST |
| E2E-07 Dataset ZIP | سرویس importer و checksum موجود | Fixture دقیق مطابق فرمت manifest، import transaction و rollback با DB ایزوله |
| E2E-08 Resumable | Service موجود | start/stop/retry واقعی در TEST + checkpoint evidence |
| E2E-09 OCR | PDF مصنوعی دو صفحه با SHA و page evidence | موتور OCR و صفحه مخدوش، دقت golden و no-hallucinated-citation |
| E2E-10 Human Review | APIهای بازبینی موجود | concurrent reviewer، optimistic version conflict و immutable evidence |
| E2E-11 Governance | lifecycle/governance موجود | permission separation، شاهد اجباری، publish/rollback و audit |
| E2E-12 Retrieval | index/query code موجود | reference scoring، alias، version filter و no workspace leak |
| E2E-13 Crawler | SSRF / redirect / size / scope guards و تست Unit با transport جعلی افزوده شد | fake HTTP transport، robots/retry/dedup + DB/HTTP/Browser evidence |

## شواهد همین بسته
- Patch با anchorهای سورس فعلی؛ old Git blob SHA و protected Windows preflight.
- PageFetcher: redirectهای explicit و same-host، منع downgrade، بررسی DNS/IP خصوصی، حد حجم 8 MiB و سقف سه redirect.
- Source validation: allowedHost دقیقاً مساوی seedHost؛ IngestionPipeline نیز before/after fetch host check و رد صریح TESTER قبل از write دارد.
- TESTER همچنان به هیچ HttpClient بیرونی متصل نمی‌شود. local fake adapter هنوز پیاده نشده است.
- PDF کاملاً ساختگی است. شماره صفحه PDF توسط PyMuPDF تأیید شد؛ هیچ استناد قانونی واقعی ادعا نشده است.
- RLS سراسری برای همه دامنه‌ها اثبات نشده. SSRF برای محافظت قطعی همچنان به firewall/egress proxy نیاز دارد تا DNS rebinding مهار شود.

## گیت‌ها
| Gate | نتیجه | دلیل |
|---|---|---|
| Repo HEAD/patch context | PASS (GitHub read) | sha ثبت شد |
| Source applied on Windows | BLOCKED | نیازمند اجرای لانچر |
| Local unit tests | BLOCKED | نیازمند Maven در Windows؛ JDK-only policy smoke در اینجا مستقل بررسی می‌شود |
| Browser/API/DB real E2E | BLOCKED | دسترسی به سرویس Windows و PostgreSQL TEST نداریم |
| External network safety | PARTIAL | بررسی URL/DNS کافی برای DNS rebinding نیست؛ egress firewall اجباری |
| Full 8-scenario E2E | BLOCKED | ۸ Executor کامل هنوز پیاده نشده‌اند |

## Handoff فاز ۵
قبل از فاز بعد، اتصال runtime DB principal محدود واقعی و ممیزی RLS، fake transport ایزوله، baseline corpus import، تست clone-only و Evidence واقعی برای ۸ Executor لازم است. هیچ DDL یا مهاجرت جدیدی در این تحویل وجود ندارد.

## جدول زیر‌فازها، آزمون و ریسک (مشاهده 2026-11-04، SHA مرجع `2a1bc507...`)

| step | files changed | assertions | observed result | risk | gate |
|---|---|---|---|---|---|
| 01 و Golden | `tests/phase04/golden/*` | ۲ صفحه فارسی واقعی داخل PDF، SHA256، منشأ مصنوعی، انتظار عددی | PDF با موتور رندر و PyMuPDF دوصفحه‌ای تأیید شد | نمونه قانون واقعی نیست | PARTIAL |
| 04 UrbanRule | فقط expected arithmetic | منشأ و محدودیت فرضی | ۳۰۰، ۱۵۰، ۶۰۰ و انتظار محاسباتی تهیه شد | انتشار/HTTP واقعی آزمایش نشده | BLOCKED |
| 07 ZIP importer | malformed ZIP، mismatched SHA | رد داده خراب بدون side effect | فقط fixture منفی تهیه شد؛ آپلود واقعی اجرا نشد | Schema واقعی بسته معتبر باید جداگانه ارزیابی شود | BLOCKED |
| 08 Pipeline | هیچ کد دامنه تغییر نکرد | checkpoint/replay | آزمون واقعی اجرا نشد | کاربر محدود DB و mock OCR لازم | BLOCKED |
| 09 OCR | `synthetic_fa_two_pages.pdf`, `golden_pages.jsonl` | page ID، PDF SHA، متن فارسی | ساخت، استخراج متن و رندر کنترل شد؛ OCR واقعی اجرا نشد | دقت OCR نامشخص | BLOCKED |
| 10 Human | هیچ کد دامنه تغییر نکرد | concurrency/version conflict | در محیط فعلی اجرا نشد | ممکن است رقیب نوشتن وجود داشته باشد | BLOCKED |
| 11 Governance | هیچ کد دامنه تغییر نکرد | شاهد قبل از انتشار و Audit | در محیط فعلی اجرا نشد | انتشار بدون تست ممنوع | BLOCKED |
| 12 Retrieval | هیچ کد دامنه تغییر نکرد | alias/version/workspace | در محیط فعلی اجرا نشد | نشت TEST/REAL باید با restricted principal سنجیده شود | BLOCKED |
| 13 Crawl | ۴ فایل موجود + policy/testهای جدید | منع SSRF/redirect/TESTER، size، same-host | هشت guard JDK21 بدون شبکه PASS؛ Maven/DB/HTTP هنوز اجرا نشده | DNS rebinding بدون فایروال پابرجاست | PARTIAL |

## بازگشت / Rollback

- این Patch هیچ Flyway Migration ندارد؛ Rollback دیتابیسی لازم نیست.
- لانچر `ApplySource` قبل از تغییر، فایل‌های اصلاح‌شده را در `SakhtYar-PatchBackups/Phase04-*/source-backup/` کپی می‌کند.
- بازگشت را فقط با بررسی `git diff` و فایل‌های Backup انجام دهید. فایل‌های جدید را بدون بررسی وابستگی‌ها حذف نکنید.
- در صورت خطای Maven، سورس برای عیب‌یابی باقی می‌ماند؛ وضعیت FAILED گزارش می‌شود. این وضعیت به معنی موفقیت E2E نیست.
- هیچ `git reset --hard`، `git clean -fd`، `DROP` یا `TRUNCATE` مورد نیاز نیست.
