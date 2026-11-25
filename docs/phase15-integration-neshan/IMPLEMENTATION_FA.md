# فاز ۱۵ | یکپارچه‌سازی نشان (Neshan) — گزارش ممیزی و پیاده‌سازی

- Repository: `HAminzadeh/SakhtYar`، شاخه: `v2`
- Git baseline: `067f4f29e26fd83688ea399d57a7277183a8784f`
- زمان HEAD: `2026-11-24T15:06:53Z`
- محدوده: `backend/modules/integration-neshan` و مستندات همین فاز
- Migration جدید: **ندارد**؛ هیچ تغییر در REAL/TEST، Schema، نقش DB یا جداول اعمال نمی‌شود.

## ۱. شواهد HEAD و تصمیمات

| مسیر / قابلیت | شاهد در HEAD | شکاف اثبات‌شده | اقدام فاز ۱۵ | اولویت | معیار قبولی |
|---|---|---|---|---|---|
| `NeshanConfiguration.neshanRestClient` | RestClient با timeout محدود موجود است | آدرس `baseUrl` قابلیت هدایت هدر محرمانه به مقصد نامجاز داشت | کنترل مقصد رسمی HTTPS و منع URL با userinfo/path/query/port غیرمجاز | P0 | تست URL معتبر/نامعتبر |
| `NeshanProperties` | enabled، baseUrl، key و timeout دارد | مجوز روشن/خاموش مستقل برای egress و تنظیم بودجه وجود ندارد | اضافه شدن `outboundEnabled=false` به شکل پیش‌فرض، و تنظیمات سقف مصرف/مدار | P0 | نبود تماس در حالت پیش‌فرض |
| `NeshanGeoProvider.geocode` | قرارداد `/geocoding/v1?json=...` موجود | خطا/بدنه نامعتبر و پاسخ بزرگ نیازمند سیاست محدودکننده هستند | اعتبار پاسخ، متن، حد پردازش و خطاهای 502 بدون افشای کلید | P1 | Mock HTTP خطاهای 401/429/خراب |
| `NeshanGeoProvider.reverseGeocode` | قرارداد `/v5/reverse` موجود | بدنه `{}` بدون داده می‌توانست به عنوان پاسخ موفق عبور کند | منع پاسخ خالی و ثبت خطای قابل کنترل | P1 | Mock 502 برای پاسخ ناقص |
| `NeshanGeoProvider` | TESTER egress block از فاز ۱۴ موجود است | به‌تنهایی تضمین محیط آفلاین نبود؛ اکانت مدیر E2E ممکن بود egress داشته باشد | مجوز مستقل و پیش‌فرض خاموشِ تمام درخواست‌های Backend نشان | P0 | تست `configured=false` با outbound=false |
| Network retry/backoff | `RestClient` بدون retry صریح | عدم وجود سیاست مشخص برای درخواست پرهزینه | Retry خودکار **عمداً فعال نیست**؛ تماس تکراری پولی/افشای دوباره موقعیت ممنوع | P1 | هر Mock فقط یک بار مصرف شود |
| Rate limiting | هیچ کنترل مصرف در حافظه وجود نداشت | فراخوانی پی‌درپی ممکن بود هزینه/429 ایجاد کند | سقف پیش‌فرض ۶۰ در دقیقه برای هر فرایند؛ 503 پس از مصرف سقف | P1 | تست با زمان مصنوعی |
| Circuit breaker | هیچ مدار محدودکننده‌ای نبود | اختلال متوالی به تماس‌های شبکه‌ای مداوم منجر می‌شد | بازشدن پس از ۵ شکست، مکث پیش‌فرض ۳۰ ثانیه | P1 | تست failure/cooldown |
| Cache | Cache مکانی موجود نبود | آدرس و نقطه دقیق داده حساس است | **Cache عمدی ندارد**؛ نگهداری حافظه‌ای موقعیت افزوده نمی‌شود | P2 | مستند و عدم وجود ذخیره‌سازی جدید |
| Map UI | `PropertyMap.tsx` از API Geo استفاده می‌کند | قرارداد API نباید تغییر کند | هیچ تغییر در UI/API، حفظ پاسخ‌ها و status | P1 | Build فرانت‌اند روی ویندوز |
| Geo RBAC | `GeoService` و `SecurityConfig` دارای CASE_READ و منع TESTER | دسترسی مستقیم به Provider باید محافظت شود | حفظ denyTester، کنترل اجباری egress و مقصد در Provider | P0 | Mock نقش TESTER |
| PostGIS/Property | فاز ۱۴ بازبینی کرده است | هیچ تغییر موردنیاز ویژه نشان مشاهده نشد | بدون Migration یا جست‌وجوی دیتابیسی جدید | P1 | بررسی عدم وجود SQL جدید |
| Test/QA | تست‌های Mock از فاز ۱۴ موجودند | تست مصرف، خطا و مقصد ناقص بود | اضافه شدن ۳ کلاس تست Phase15 + حفظ Phase14 | P0 | Maven/JUnit واقعی |

## ۲. نقشه وابستگی

`PropertyMap.tsx` → `/api/v1/geo/{status,geocode,reverse}` → `GeoController` → `GeoService` → `GeoProvider` → **`NeshanGeoProvider`** → `RestClient` → `api.neshan.org`.

`GeoService` مجوز CASE_READ را بررسی می‌کند. `NeshanGeoProvider` خروجی network و مجوز egress را مستقل کنترل می‌کند. `NeshanTrafficGuard` بودجه per-process و شکست‌های پی‌درپی را مدیریت می‌کند. `NeshanDestinationPolicy` مقصد مجاز را تعیین می‌کند. کلید سرویس در سمت سرور باقی می‌ماند و نباید به frontend انتقال پیدا کند.

## ۳. تنظیمات اجرای امن

در نسخه جدید فعال‌کردن `NESHAN_ENABLED=true` و تنظیم `NESHAN_SERVICE_API_KEY` **به‌تنهایی کافی نیست**. مقدار `outboundEnabled` به‌طور پیش‌فرض `false` است و برنامه اجازه تماس واقعی نمی‌دهد.

برای یک محیط عملیاتی مجاز و پس از تأیید سیاست داده موقعیت، تنظیم متغیرهای Environment مربوط به Spring Boot:

```properties
NESHAN_ENABLED=true
NESHAN_SERVICE_API_KEY=<secret-from-secret-store>
APP_INTEGRATIONS_NESHAN_OUTBOUND_ENABLED=true
APP_INTEGRATIONS_NESHAN_MAX_REQUESTS_PER_MINUTE=60
APP_INTEGRATIONS_NESHAN_CIRCUIT_FAILURE_THRESHOLD=5
APP_INTEGRATIONS_NESHAN_CIRCUIT_OPEN_SECONDS=30
```

`NESHAN_BASE_URL` اگر تنظیم شود باید `https://api.neshan.org` باشد؛ HTTPS با پورت ۴۴۳ و `/` خالی نیز مجاز است. استفاده از HTTP، host دیگر، IP داخلی، query و userinfo ممنوع است. برای محیط تست مقدار `APP_INTEGRATIONS_NESHAN_OUTBOUND_ENABLED=false` را حفظ کنید. از `NESHAN_SERVICE_API_KEY` واقعی در تست استفاده نکنید.

**مهم:** وب‌نقشه نشان در مرورگر از `VITE_NESHAN_MAP_API_KEY` و اتصال مستقیم کلاینت به Tileهای نشان استفاده می‌کند. این فاز فقط درخواست‌های Backend نشان را کنترل می‌کند؛ برای Browser E2E آفلاین باید Tile/Style requests نیز در ابزار مرورگر Mock یا Block شوند.

## ۴. سیاست تاب‌آوری و محرمانگی

- Timeout اتصال/پاسخ: پیش‌فرض ۱۰ ثانیه، سقف ۳۰ ثانیه (از فاز ۱۴ حفظ شد). تغییر مسیر HTTP (Redirect) غیرفعال است تا هدر Api-Key به میزبان دیگری منتقل نشود.
- Rate limit: پیش‌فرض ۶۰ در دقیقه **به‌ازای هر instance** (حد مجاز تنظیم ۱ تا ۶۰۰؛ مقادیر نامعتبر به پیش‌فرض تبدیل می‌شوند).
- Circuit breaker: ۵ خطای پیاپی، ۳۰ ثانیه وقفه؛ قابل تنظیم و با حدود دفاعی ۱..۲۰ و ۱..۳۶۰۰.
- Retry/backoff: **خاموش**؛ بدون retry برای عملیات GET حاوی آدرس/مختصات. Backoff از طریق circuit breaker (نه تلاش شبکه مجدد) است.
- Cache: **خاموش** برای جلوگیری از انباشت آدرس خصوصی. این تصمیم آگاهانه است.
- پاسخ بزرگ‌تر از ۶۵٬۵۳۶ کاراکتر، پاسخ تهی، JSON ناقص، مختصات خارج از WGS84 یا متن نامعتبر، به 502 عمومی تبدیل می‌شود.
- محدودیت ۶۵K پس از دریافت String اعمال می‌شود؛ در transport محدودیت سخت حجم بایت قبل از تخصیص حافظه محسوب نمی‌شود. برای حفاظت سنگین‌تر از ingress/egress proxy با سقف response size استفاده شود.
- خطاهای 401/403/429 به 503 عمومی و خطاهای غیرمنتظره Provider به 502 نگاشت می‌شوند؛ متن/پاسخ Provider، secret، URI یا مختصات در پیام خطای عمومی نمایش داده نمی‌شوند.
- هیچ جدول، Queue، Audit حاوی موقعیت یا ذخیره کلید جدید ایجاد نشده است.
- این محافظت‌ها جایگزین Quota اشتراکی برای Cluster و egress firewall نیستند.

## ۵. تست، محدودیت‌ها و Gate

| بررسی | وضعیت تحویل | دلیل |
|---|---|---|
| خواندن HEAD و کد واقعی | PASS | شناسه فوق از GitHub |
| Java 21 compilation با Stub برای Policies | PASS | کنترل آفلاین، نه Maven واقعی |
| Smoke مستقل مقصد، Rate و Circuit | PASS | ۱۴ بررسی با ساعت مجازی |
| تست‌های Phase15 JUnit و Mock HTTP | READY_TO_VERIFY | روی محیط Maven کاربر اجرا شوند |
| رگرسیون Phase14 Neshan + Geo | READY_TO_VERIFY | با Verify/Full Maven |
| DB/PostGIS/RLS | NOT_RUN | این فاز DB migration ندارد و هیچ DB در Sandbox لمس نشده |
| frontend `npm run build` | READY_TO_VERIFY | اجرا روی ماشین کاربر |
| Live Neshan | INTENTIONALLY_NOT_RUN | کلید واقعی و تماس پولی استفاده نمی‌شود |
| Browser E2E آفلاین | BLOCKED | نیازمند Mock درخواست‌های Tile در مرورگر |
| Full reactor | READY_TO_VERIFY | اجرای جدا پس از Verify محدود |

**Acceptance status:** `READY_TO_VERIFY`؛ پذیرش قطعی فقط پس از تست‌های واقعی و بررسی Runtime در محیط کاربر.

## ۶. راهنمای اجرا

ZIP و نصب‌کننده را در `Downloads` بگذارید. اسکریپت در حالت‌های `SelfTest`, `Audit`, `DryRun`, `ApplySource`, `Verify`, `Rollback` کار می‌کند. مسیر پیش‌فرض ریشه: `D:\ChatGPT_Projects\SakhtYar\source`. در صورت مغایرت HEAD، منشأ مخزن، blob فایل‌های قدیمی، تغییرات محلی/staged فایل هدف یا SHA256، نصب fail-closed خواهد بود. Backup خارج از مخزن در `SakhtYar-PatchBackups` قرار می‌گیرد. Rollback فقط در صورت تطبیق هش فایل‌های جدید امکان‌پذیر است. هیچ Flyway/migration/database request اجرا نمی‌شود.

`Verify` به‌صورت محدود، تست‌های Mock ماژول و وابستگی‌هایشان را با Maven اجرا می‌کند؛ اگر Maven/Node نصب نباشد وضعیت BLOCKED است نه PASS.
