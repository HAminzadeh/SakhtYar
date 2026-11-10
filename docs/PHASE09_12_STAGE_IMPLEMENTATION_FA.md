# فاز تکمیلی ۰۱ — بسته یکپارچه ۱۲ مرحله‌ای برای Development/Production و E2E از UI

## محدودیت غیرقابل‌تغییر

پروفایل development به معنی مجوز نوشتن بر REAL یا TEST نیست. Session فعلی فقطخواندنی است؛ اجرای کامل Business E2E در صورت نبود شواهد واقعی جداسازی `BLOCKED` است. ساخت Clone، اجرای Migration/Seed، ایجاد/تغییر Role یا RLS و پاک‌سازی خودکار SQL در Installer انجام نمی‌شود.

| مرحله | خروجی پیاده‌سازی | سطح اعتبار فعلی |
|---|---|---|
| ۱ | مبنا: GitHub `v2`، SHA `6bb50f3026182cc8048e31b0dc82cdbbb1679222` و ۸ تغییر محافظت‌شده | قرارداد نصب با Git blob |
| ۲ | `application-development.yml`، امکانات Preflight در UI، Flyway/Seed غیرفعال | تنظیمات واقعی؛ تست Runtime بعد از اجرا |
| ۳ | `application-production.yml` و حذف Controllerهای Simulation از Beanهای Production | حذف مسیر HTTP در سطح Spring، نیازمند تست Runtime |
| ۴ | `DevSeedRunner` غیرفعال در هر دو پروفایل، Guard خیلی زودتر از AutoConfig و guard ثانویه | بدون تغییر موقت global ENV |
| ۵ | دو `.run` برای IntelliJ با پروفایل‌های مشخص | ساختار معمول IntelliJ؛ نام module باید با Import Maven سازگار باشد |
| ۶ | کاتالوگ ۱۱ مانع با راهکار فارسی، نوع شاهد، و گزارش JSON خواندنی | شواهد بدون جعل PASS |
| ۷ | پروب خواندنی PostgreSQL با LOGIN فیزیکی جدا، بدون DataSource اصلی، بدون DDL/Role | اگر Credentials محدود از پیش موجود نیست BLOCKED |
| ۸ | گزارش RLS چهار جدول CORE؛ سایر جدول‌ها و مسیرها هنوز نیازمند ممیزی مستقل | پوشش کامل برنامه **اثبات نشده** |
| ۹ | کنترل‌کننده Session خواندنی با عمر حداکثر ۱۵ دقیقه، ظرفیت ۳۲، شناسه Run و مالک Workspace | Metadata فقط حافظه و بدون Business Write |
| ۱۰ | موجودی Fake Adapterهای Phase02 حفظ شد؛ نبود تزریق سراسری واقعی به‌صراحت BLOCKED باقی ماند | خروجی واقعی ممنوع تا اثبات کامل |
| ۱۱ | پایان Session: حذف از RAM؛ بدون صدور DELETE SQL یا دستکاری پیکربندی | منابع دیتابیس ساخته نشده‌اند تا پاک شوند |
| ۱۲ | UI جدید، تست‌های JUnit/Node، workflow CI، ZIP و PowerShell Audit/Apply/Verify/Tests | نتیجه BUILD ویندوز باید از گزارش واقعی خوانده شود |

## رفتار پروفایل‌ها

- `development`: `app.dev-seed.enabled=false`، `spring.flyway.enabled=false`، حالت عادی کسب‌وکار فعال، Session **خواندنی** E2E فعال، Business Write همچنان مشروط به Phase08 و اتصال محدود.
- `production`: همان دو قفل؛ کنترلرهای Simulation و Business E2E ثبت نمی‌شوند و فقط API اطلاع‌رسانی پروفایل برای مدیر فعال است.
- `local` قدیمی دست‌نخورده باقی می‌ماند برای عدم شکست ناگهانی IDEهای قدیمی؛ ولی درخواست اجرای تجاری با policy جدید در عمل پذیرفته نمی‌شود. برای Development رسمی از Run Config جدید استفاده می‌شود.

## نکته اتصال مستقل TEST

برای اثبات بخشی از پیش‌نیازهای PostgreSQL، فقط اگر قبلاً نقش محدود و credential معتبر مستقل در محیط توسعه وجود داشته باشد، تنظیمات اختیاری زیر خوانده می‌شود:

- `SAKHTYAR_E2E_PROBE_JDBC_URL`
- `SAKHTYAR_E2E_PROBE_USERNAME`
- `SAKHTYAR_E2E_PROBE_PASSWORD`

این تنظیمات را Installer درخواست نمی‌کند، در Git ثبت نمی‌کند و نمی‌سازد. پروب هیچ‌گاه اتصال اصلی برنامه، `SET ROLE`، `CREATE ROLE` یا Seed را جایگزین هویت فیزیکی نمی‌کند. مشاهده صفر ردیف REAL بدون کنترل مثبت مستقل، اثبات کامل جداسازی نیست.

## مسیر کاربری پس از نصب

۱. در IntelliJ گزینه `SakhtYar Backend - Development` را اجرا کنید. ۲. Frontend را مانند قبل اجرا کنید. ۳. در Simulation Center، Workspace فعال را انتخاب کنید. ۴. بخش فاز ۰۸/۰۹ را باز کرده، Session بررسی خواندنی ایجاد کنید، موانع را ببینید و JSON بگیرید. ۵. جلسه را خاتمه دهید یا بگذارید پس از ۱۵ دقیقه از RAM حذف شود. ۶. ۲۴ Diagnostic فاز ۰۷ را جداگانه اجرا کنید. ۷. Business E2E تا تأمین شواهد واقعی BLOCKED می‌ماند.

## مواردی که به‌طور عمدی انجام نشده‌اند

- اجرای واقعی ۲۴ گردش‌کار تجاری، تغییر جدول‌ها یا FKها، ساخت نقش یا دیتابیس، تزریق Fake Adapter به تمامی سرویس‌های واقعی، پاک‌کردن رکوردهای دیتابیس، Restore و اجرای مرورگر در حالت متصل به دادهٔ واقعی.
- هیچ کدام از موارد فوق را نمی‌توان بدون سورس کامل هر آداپتر، Credential محدود و بررسی محیط واقعی به‌صورت امن PASS اعلام کرد.

## بهسازی‌های بعدی

اتصال مثبت کنترل Cross Workspace با دادهٔ موجود در TEST، تکمیل full application scope، تغذیه EvidenceStore و پیاده‌سازی Executorهای فاز ۲ و ۳؛ بدون غیرفعال‌کردن Gate فعلی.
