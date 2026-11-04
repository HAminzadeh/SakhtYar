# فاز ۲ — گزارش شکاف‌های دیتابیس مشترک REAL/TEST

مبنای سورس: GitHub `HAminzadeh/SakhtYar`، شاخه `v2`، SHA `6a82eb116d524bc6d8705023f6e2235557240f73`، چهارم نوامبر ۲۰۲۶.

| حوزه | کد تهیه‌شده در این Patch | آنچه هنوز اثبات یا اجرا نشده | وضعیت |
|---|---|---|---|
| V58 و تگ‌ها | از تگ‌های فعلی استفاده می‌شود | تست Schema همه جدول‌ها در DB زنده | BLOCKED |
| V65/V66 | Migration افزایشی Ledger و حالت SHARED_DB_TEST_GATED و محافظ egress | Flyway apply و verify روی دیتابیس اصلی پس از backup | BLOCKED |
| Runtime role | هیچ SUPERUSER یا BYPASSRLS جدیدی مجاز نشده | اتصال مستقلِ Runtime محدود و audit نقش‌ها | BLOCKED |
| CORE چهارجدولی | حفاظت عمومی Case/Property/Owner/Document و هویت TESTER | آزمون واقعی API و SQL و نقش مستقل | BLOCKED |
| بقیه دامنه‌ها | Factory declarations مطابق shared DB | RLS/trigger/FK/role به تفکیک هر دامنه؛ FixtureWriter واقعی | BLOCKED |
| اعلان | کنترل TESTER و GUC، REAL-only queue rows، V66 trigger | تست Integration واقعی outbox worker | BLOCKED |
| Crawler | قطع HTTP در PageFetcher برای TESTER | background/job propagation و network deny زیرساخت | BLOCKED |
| AI | قطع دسترسی TESTER در AI gateway و Ollama | تمامی ابزارها، thread propagation، mock provider | BLOCKED |
| Neshan | قطع تماس TESTER در geocode و reverse | تمام API provider integrations و mock | BLOCKED |
| MinIO | جلوگیری از بارگذاری/دانلود TEST در مسیر عمومی REAL | Adapter امن TEST با namespace workspace/run | BLOCKED |
| Redis | helper قبلی وجود دارد و دست‌نخورده | اعمال prefix در تمام Consumerها | BLOCKED |
| FixtureFactory 24 | قرارداد SHARED_DB_TEST_GATED برای ۲۳ مورد و E2E14 محدود | ۲۴ سناریوی end-to-end کامل | BLOCKED |
| Cleanup | قرارداد محافظت از run+workspace | transaction-safe cleanup با FK، rollback، retention | BLOCKED |

**تفاوت حیاتی:** نوشته شدن سورس، Build موفق و حتی Unit Pass هیچ‌کدام به‌معنای PASS شدن ۲۴ گردش‌کار تجاری نیست. `V66` به جدول metadata فاز۲ محدود است و مهاجرت ۵۷ جدول و RLS سراسری انجام نمی‌دهد. این انتخاب عمدی است تا اطلاعات REAL با سیاست‌های کورکورانه قفل یا تغییر نشود.

## راهنمای عملیاتی

1. اجرای installer فقط سورس را نصب و Build/Unit Tests را اجرا می‌کند. هیچ SQL خودکار روی REAL اجرا نمی‌شود.
2. از PostgreSQL اصلی backup معتبر بگیرید و صحت backup را کنترل کنید؛ Flyway در اجرای بعدی Spring Boot می‌تواند V65/V66 را اعمال کند.
3. پس از بالا آمدن برنامه، فقط روی دیتابیس اصلی با queryهای خواندنی `tests/phase02/04_shared_db_gap_audit_readonly.sql` تاریخچه Flyway و RLS را بررسی کنید.
4. برای تبدیل هر Fixture به اجرایی، باید نقش محدود مستقل، RLS آن دامنه، Session Scope امن، FK Cross-Scope و عدم ارسال بیرونی ثابت شود.
