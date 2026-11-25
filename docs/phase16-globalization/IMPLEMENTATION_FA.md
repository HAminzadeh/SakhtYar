# فاز ۱۶ — بومی‌سازی، داده مرجع و تنظیمات منطقه‌ای ساخت‌یار

**Git baseline:** `75ce9aad000aa0601fb382eefa9204434f611d4e` — شاخه `v2`، 2026-11-25 04:45:02 UTC.  
**Acceptance state:** `READY_TO_VERIFY`؛ کامپایل/تست روی محیط اصلی هنوز انجام نشده است.

## معماری

- `GlobalController`: API کاتالوگ، ترجیحات، Jurisdiction و FX؛ قراردادهای قبلی حفظ شده‌اند و `/fx/quote` و `/fx-rates/{id}/review` اضافه شده‌اند.
- `JurisdictionService`: دسترسی بر اساس `CaseAccessService` و موجودیت `CaseEntity`؛ تراکنش نوشتن با lock پرونده و محدودیت `REAL`.
- `FxService`: ثبت نرخ در `PENDING`، تصمیم ادمین، تنها نرخ‌های `APPROVED`، کنترل `valid_from/valid_to` و انتخاب قطعی نرخ بر اساس تاریخ و ID.
- `GlobalizationPolicy`: قوانین مستقل BigDecimal، جست‌وجو، IANA time zone و تاریخ/اعداد.
- `UserPreferenceService`: تغییرات غیر-null به صورت merge؛ فیلدهای قبلی حذف نمی‌شوند؛ Round-trip و Audit.
- `CatalogService`: جست‌وجوی محدود با نرمال‌سازی فارسی/عربی و escape کردن wildcardهای SQL.
- `GeoNamesImporter`: مجوز ادمین، outbound با default=false، مقصد HTTPS ثابت، منع redirect و محدودیت داده فشرده.
- `MasterDataStartup`: Import خودکار حتی در صورت فعال بودن گزینه قدیمی انجام نمی‌شود؛ Import باید دستی و مجاز باشد.
- `CurrencyCatalogBootstrap`: عملیات Startup فقط با فعال‌سازی صریح جداگانه ثبت می‌شود؛ دیتابیس اصلی را بدون مجوز پیکربندی تغییر نمی‌دهد.
- Frontend: تنظیمات تقویم، اعداد و شروع هفته؛ نمایش شمسی با native Intl و رفع وابستگی انتخاب استان/شهر.

## مفاهیم مالی و محدودیت‌ها

- تومان با ریال ضریب قطعی ۱۰ دارد؛ این نسبت از seed موجود (`V24`) پشتیبانی می‌شود.
- نرخ `APPROVED` یک رکورد اعلامی و بررسی‌شده مدیریتی است؛ **معادل تأیید رسمی بانکی نیست**.
- `/api/v1/global/fx/quote` شامل `rateId`, `rate`, `sourceLabel`, `observedAt`, `inverse` و `stale` است. مقدار `stale` برای نرخ بیش از ۳۰ روز نسبت به زمان تبدیل true می‌شود؛ برای رابطه ریال/تومانِ ثابت می‌تواند صادق باشد و الزاماً به معنای نامعتبر بودن ضریب تبدیل نیست.
- Endpoint سازگار `/api/v1/global/fx/convert` همچنان `{amount,converted}` بازمی‌گرداند، اما فقط نرخ تأییدشده را مصرف می‌کند. خروجی با minor-unit ارز مقصد گرد می‌شود. بدون نرخ تأییدشده خطای مشخص صادر می‌شود؛ نرخ ساختگی یا fallback بی‌منبع ممنوع است.
- فرض امنیتی: تنها نقش دارای `USER_MANAGE` اجازه ثبت/تصمیم نرخ و مدیریت Import دارد؛ سرویس نیز این مجوز را کنترل می‌کند. بررسی تفکیک دو نفر برای ثبت/تأیید نیازمند مدل ثبت کننده نرخ در Migration جدید و تصمیم کسب‌وکار است، در این بسته فعال نیست.
- مقدار null در `PreferenceUpdate` به معنی «حفظ مقدار فعلی» است؛ برای clear کردن فیلدها endpoint مستقل PATCH با semantics سه‌حالتی لازم خواهد بود.

## GeoNames و تغییرات داده

- `app.globalization.master-data.outbound-enabled` به صورت پیش‌فرض false است. برای Import حقیقی، مجوز مدیر، کلید تنظیمات صریح و محیط مورد تأیید ضروری است.
- `MasterDataStartup` از شبکه استفاده نمی‌کند. `CurrencyCatalogBootstrap` فقط با `app.globalization.master-data.bootstrap-currency-on-startup=true` راه‌اندازی می‌شود؛ در `local`/`development` توصیه نمی‌شود.
- دانلود GeoNames فقط `https://download.geonames.org/export/dump/`، بدون redirect و حداکثر ۳۰۰ MiB داده فشرده است. برای خواندن حجم بسیار زیاد ZIP و اجرای چند-instance، آزمون Clone/Load و serialization سراسری هنوز لازم است؛ اجرا در محیط عملیاتی تا PASS شدن Gate ممنوع است.
- هیچ Migration قدیمی دستکاری نشده و Migration جدید لازم نشد. داده‌های قبلی و RLS باید روی Clone PostgreSQL سنجیده شوند. `V23–V29`, `V38`, `V58–V68` باید با محیط هدف سازگار باشند.

## تقویم و بومی‌سازی

- Backend زمان را در `Instant`/UTC نگاه می‌دارد؛ Frontend تبدیل نمایشی را با `Intl.DateTimeFormat('fa-IR-u-ca-persian', {timeZone:'Asia/Tehran'})` انجام می‌دهد. این به معنی تبدیل تاریخ ورودی آزاد به تاریخ قابل ذخیره در Backend نیست.
- تاریخ میلادی با `-u-ca-gregory`، فرمت ISO با timestamp و مقادیر عددی بدون float برای محاسبات مالی استفاده می‌شود.
- گزینه‌های تاریخ `ISO`, `GREGORIAN`, `JALALI`، اعداد `LATIN`,`PERSIAN`,`ARABIC` و اولین روز هفته ۱ تا ۷ معتبرند. برای ترجیحات ناشناخته legacy نیاز به migration داده بعد از audit وجود دارد.
- موتور `RuntimeUiLocalizer` موجود بازنویسی نشده تا ریسک تغییر متن‌های کاربر در UI افزایش نیابد؛ تست کامل RTL و ترجمه‌های همه صفحات همچنان Gate مربوط به مرورگر است.

## تست و استقرار

1. بسته ZIP + Launcher PS1 را در Downloads قرار دهید.
2. SelfTest, Audit, DryRun را ابتدا اجرا کنید؛ این سه حالت سورس را تغییر نمی‌دهند.
3. ApplySource فقط روی `v2` و Commit پایه، فایل‌های بدون تغییر و SHAهای معتبر عمل می‌کند و Backup ثبت می‌کند.
4. Verify با Maven `-o -pl modules/globalization -am clean test` و `npm run build` اجرا می‌شود؛ نه Flyway، نه PostgreSQL و نه GeoNames خارجی.
5. قبل از Import واقعی یا Deploy: Clone Postgres و role محدود، تست RLS، آزمون ۲ کاربر/۲ پرونده و بررسی Duplicate FX، تست هم‌زمانی Import، تست DST و Jalali UI و E2E مرورگر را اجرا کنید.
6. Rollback فقط سورس را با Backup برمی‌گرداند، تغییرات دیتابیس/خارجی را بازنمی‌گرداند. این بسته Migration ندارد.

## محدودیت‌های پذیرش

- تست واقعی PostgreSQL و end-to-end قابل ادعا نیستند.
- منبع FX مستقل و سرویس نرخ زنده به این فاز اضافه نشده؛ هیچ API خارجی نرخ ارز فراخوانی نمی‌شود.
- کش برای Global Catalog ایجاد نشده چون قبل از benchmark و invalidate، صحت/ریسک آن قابل اثبات نیست.
- Distributed lock سراسری Import هنوز نیازمند آزمون روی کلاستر و Database Clone است؛ `AtomicBoolean` فعلی فقط درون همان Instance عمل می‌کند.
- عدم تضمین کامل صحت GeoNames داده‌ای از منبع خارجی؛ checksum و dataset version هنوز از Provider دریافت و به شکل قابل اعتماد ثبت نمی‌شوند.

## مسیرهای کلیدی

`backend/modules/globalization/src/main/java/com/sakhtyar/globalization/`، `backend/modules/identity/.../SecurityConfig.java`، `frontend/src/pages/PreferencesPage.tsx`، `frontend/src/features/case/GlobalLocationSelector.tsx`، `frontend/src/i18n/formatters.ts`.
