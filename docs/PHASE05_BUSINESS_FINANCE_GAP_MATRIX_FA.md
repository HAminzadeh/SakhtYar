# فاز ۵ — گزارش شکاف واقعی و تحویل مرحله‌ای

SHA بررسی‌شده GitHub: `578024f09d0ece8879770fc47df777a70c83e848`؛ زمان تهیه: 2026-11-07.

| سناریو | یافته در سورس | این بسته | دروازه اجرای کامل |
|---|---|---|---|
| E2E-15 سازندگان | BuilderService و BuilderMatchingService، نیازمند ACL پیش از خواندن و لینک‌سازی | کنترل سطح پرونده و ورودی سرمایه منفی | ثبت/تطبیق/پیشنهاد با Fixture و HTTP/DB واقعی |
| E2E-16 مصالح | PriceStatistics و AssemblyCostCalculator موجود؛ واحدها فقط در حالت یکسان معتبر | رد قیمت‌های منفی/Null + Golden هزینه اسمبلی | نرخ/آنومالی/محاسبه با ثبت snapshot و کنترل واحد/ارز |
| E2E-17 سناریو | Snapshot هزینه و Quality Package وجود دارد | Golden محاسبات مرجع مشترک | ساخت چند سناریو، snapshot immutability و تثبیت نسخه قیمت |
| E2E-18 امکان‌سنجی | UrbanRuleEvaluator و FeasibilityCalculator موجودند | Golden FAR مصنوعی | اثبات منشأ Rule، PASS/FAIL، نقش و DB fixture |
| E2E-19 مشارکت مالی | تقسیم سهم مستقل گرد می‌شد؛ API مستقیم case ACL نداشت | حفظ دقیق جمع مبلغ‌ها + requireWrite/read | ۶۰/۴۰ و ۱۰۰٪، HTTP واقعی و قفل DB TEST |
| E2E-20 حساسیت | baseline نامعتبر ممکن بود NullPointerException بدهد؛ IDOR مسیر مستقیم | بررسی baseline + ACL + Golden سناریو | شبکه شوک، lineage و اثبات عدم نشت REAL |

## وضعیت و نتایج

- **تأییدشده:** بررسی واقعی HEAD، فایل‌های دامنه، قرارداد API و فرمول‌ها؛ ساخت بسته و Hash فایل‌ها. Goldenها با Python Decimal مستقل محاسبه شده‌اند.
- **نیازمند اجرا در Windows:** Maven JUnit، Build کامل Spring Boot و Frontend، Startup، Integration، PostgreSQL و Browser.
- **E2E-15..20:** تا اجرای واقعی workflow و تأیید Positive/Negative/DB/HTTP/Browser همگی `BLOCKED`.
- **همهٔ ۲۴ E2E:** مطابق آخرین گزارش ارائه‌شده، ۰ PASSED / ۲۴ BLOCKED. بستهٔ حاضر وضعیت E2E را دستکاری نمی‌کند.
- **Security:** نقش TESTER و PostgreSQL principal یکی نیستند. تست کامل نباید با superuser/BYPASSRLS انجام شود. فایل SQL صرفاً خواندنی است.
- **Migration:** نسخه جدیدی ساخته نشده؛ V66 همچنان آخرین نسخه مشاهده‌شده است. rollback سورس از Backup نصب‌کننده؛ rollback دیتابیس موضوعیت ندارد.
- **ریسک باقی‌مانده:** دسترسی‌های دامنه‌های مجاور builder/material/scenario و CRUDهای global هنوز نیازمند ممیزی و تست HTTP/DB هستند. صرف guards اضافی، اثبات ایزوله‌سازی نیست.

## Handoff فاز ۶

اول E2E-01، E2E-02، E2E-05 و E2E-14 را با هویت و DB principal محدود و دیتای TEST امن باز کنید. سپس شش Executor این فاز را به quality اضافه کنید؛ PASS تنها با evidence واقعی و تأیید عدم تغییر REAL. در فاز ۶ باید Snapshot/Lineage و گردش کار انتها به انتها کنترل شود.
