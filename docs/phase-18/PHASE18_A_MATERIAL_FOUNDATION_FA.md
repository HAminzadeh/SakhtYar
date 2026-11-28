# فاز 18-A — زیرساخت و امنیت مصالح

مبنا: `v2` در `a2392904130bcaaadacfc925cf4f23c6f6e311a3`، تاریخ 2026-11-26T10:15:32Z.

## دامنه و تصمیم‌ها
- 19 فایل اصلی Java، 4 فایل تست قبلی، 13 Endpoint، پنج جدول V12؛ V26 ظرفیت ارز را به 8 و کنترل global_currency فعال را افزوده است. این قرارداد دوباره پیاده‌سازی نشده است.
- دسته‌بندی فعلاً متن استاندارد و آزاد است؛ طبقه‌بندی بسته بدون شواهد دامنه تحمیل نشده است. نام فارسی با NFC و ی/ک و فاصله استاندارد می‌شود، جست‌وجو نیم‌فاصله را نیز نادیده می‌گیرد. جست‌وجو همچنان در حافظه است؛ pagination و بهینه‌سازی در فاز C.
- کد جدید ASCII، بدون فاصله، uppercase و یکتا؛ Migration V69 در صورت وجود برخورد case-insensitive متوقف می‌شود، هیچ رکوردی را تغییر نمی‌دهد.
- واحد پایه غیرقابل تغییر است، حتی پیش از ثبت قیمت؛ واحدهای قدیمی ناشناخته هنگام ویرایش حفظ می‌شوند. برای تغییر معنای واحد یا مشخصات Variant دارای سابقه، رکورد تازه بسازید و قبلی را غیرفعال کنید. Variant بدون وابستگی قابل ویرایش است. نوع/وزن بسته یا چگالی از روی نام حدس زده نمی‌شود.
- GET `/api/v1/materials/units` فهرست واحدها و عامل نسبت به واحد مرجع هر بعد را می‌دهد؛ PUT `/api/v1/materials/{id}/variants/{variantId}` همان قرارداد CreateVariantRequest را استفاده می‌کند. endpointهای قبلی حذف نشده‌اند. ثبت مشاهده برای واحد متفاوت تا فاز B ممنوع است؛ helper تبدیل دقیق از هم‌اکنون آماده است.
- مقدار `NUMERIC(20,6)` و مبلغ `NUMERIC(20,2)` و confidence مطابق ستون‌ها اعتبارسنجی می‌شوند؛ داده بیش از دقت رد می‌شود و silently round نمی‌شود. در HTTP، @Digits نیز ظرفیت را کنترل می‌کند. V26 کد ارز فعال را enforce می‌کند. تبدیل ریال/تومان و سایر FX در B، با قرارداد GlobalizationPolicy موجود، انجام می‌شود.
- پیوند جدید KnowledgeTerm باید APPROVED باشد؛ پیوند قبلی بدون تغییر باقی می‌ماند. منبع قیمت همچنان باید موجود باشد؛ governance منبع، زمان، تکرار، review و outlier موضوع B است.
- RBAC HTTP: GET با CASE_READ یا USER_MANAGE، POST/PUT با USER_MANAGE؛ OPTIONS طبق قرارداد CORS موجود مجاز، سایر متدها deny. نقش TESTER دسترسی مستقیم به مسیر مصالح ندارد. APIهای داخلی Assembly به صورت مستقل در C بررسی می‌شوند.
- RLS روی پنج جدول با FORCE و دو policy (allow و restrictive)، بررسی والد/variant/source و تغییرناپذیری scope. دسترسی TEST علاوه بر GUC به login فیزیکی `sakhtyar_tws_<workspace uuid without hyphens>` نیاز دارد. نقش runtime عادی با GUC تست نمی‌تواند TEST ببیند؛ نقش workspace با GUC REAL نیز نمی‌تواند REAL ببیند. هیچ GRANT، ROLE، seed، تغییر وضعیت داده یا تماس خارجی در migration نیست. حساب‌های superuser/BYPASSRLS خارج از مرز RLS هستند و باید از runtime حذف شده باشند. قبل از تست تجاری باید privileges و workspace provisioning تکمیل و آزموده شوند؛ این بسته خود به workspace نقش یا مجوز نمی‌دهد. FKهای ورودی از سایر ماژول‌ها هنوز کامل scope نشده‌اند؛ این فاز اثبات سراسری نیست.
- حذف مستقیم material_item، material_variant و material_price_observation پس از V69 ممنوع؛ غیرفعال‌سازی جایگزین حذف است. aggregate و anomaly تحت scope خود قابل حذف هستند؛ governance بیشتر در B. قید UNIQUE کد موجود سراسری است؛ TEST آینده باید از namespace مجزا استفاده کند.
- V69 migration افزایشی است؛ migration تاریخی تغییر نمی‌کند. SQL clone review و آزمون با حساب محدود قبل از production الزامی است. بسته صرفاً سورس می‌نویسد و SQL/Flyway اجرا نمی‌کند.

## قبل از هر اجرای برنامه
1. Audit، DryRun، ApplySource، Verify را اجرا کنید. Verify فقط فایل‌ها و در صورت `-RunMaven` تست‌های منتخب واحد را اجرا می‌کند. Maven کامل جداگانه و بدون اتصال به DB واقعی انجام شود.
2. از DB نسخه پشتیبان معتبر بگیرید؛ Migration را ابتدا فقط روی clone ایزوله با runtime غیرسوپریوزر اجرا کنید. preflight برخورد کد و روابط ناسازگار را بدون تغییر داده متوقف می‌کند. دیتابیس فعلی از این محیط قابل بررسی نبود.
3. فایل `phase18_a_material_readonly_audit.sql` را با runtime role واقعی (نه postgres ادمین) روی clone اجرا کنید؛ FORCE RLS و دو policy و trigger برای هر 5 جدول، false برای arbitrary_test_allowed، و rolsuper/rolbypassrls=false انتظار می‌رود. این گزارش جای تست نفوذ یا آزمون منفی را نمی‌گیرد.
4. روی clone با TEST workspace و login اختصاصی، بدون GRANT سراسری، کنترل کنید: workspace A فقط A را ببیند؛ A نتواند به B یا REAL دسترسی داشته باشد حتی با تغییر GUC؛ role عادی نتواند TEST بخواند یا بنویسد؛ parent/source/variant ناسازگار رد شود؛ تغییر scope و حذف شواهد رد شود. هیچ fixture روی REAL ساخته نشود. اگر provisioning امن وجود ندارد، این تست BLOCKED است.
5. RBAC HTTP: ناشناس 401، TESTER فاقد دسترسی، CASE_READ فقط GET، USER_MANAGE GET/POST/PUT، CSRF فعال؛ PATCH/DELETE رد. این موارد باید روی محیط ایزوله تأیید شوند.
6. rollback سورس فقط پیش از اجرای migration یا پس از تهیه برنامه سازگار با DB. پس از اعمال V69 هرگز فایل migration اعمال‌شده را حذف نکنید؛ برای DB از restore clone یا migration اصلاحی افزایشی استفاده کنید. Installer در Rollback از شما تأیید `-SourceOnlyRollbackConfirmed` می‌خواهد؛ وضعیت DB را نمی‌داند.

## Gate
READY_TO_VERIFY (سورس)، BLOCKED (runtime/DB isolation). تا Phase18-B اصلاح تجمیع quantityBasis، anomaly، freshness و immutable aggregate لازم است. V13 `cost_estimate_line.aggregate_id` به aggregate با ON DELETE RESTRICT ارجاع دارد، بنابراین rebuild فعلی پس از برآورد ممکن است شکست بخورد؛ در B/C بدون حذف snapshot اصلاح شود. این نسخه به تنهایی مجوز انتشار production نیست.
