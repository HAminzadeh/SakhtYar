# Phase 18-C — اتصال محصول و انتشار کنترل‌شده

مبنا: شاخه `v2`، SHA `8aa7d0a0c77ef6639fd2576ad546aada3e816b57`، ۲۸ نوامبر ۲۰۲۶. CI مبنا: ۱۲ Job موفق، release-acceptance رد نشده اما SKIPPED؛ این شواهد متعلق به سورس قبل از نصب بسته C هستند.

## معماری و تصمیم‌های طراحی
- قیمت مرجع 2.0 فقط با همان مصالح، گونه، نوع قیمت، ارز، واحد پایه، استان و شهر و در بازه اعتبار V70 انتخاب می‌شود. نبود یا ابهام قیمت، توقف محاسبه است. تبدیل FX جدید نداریم. واحد BOM باید همان واحد پایه باشد؛ ثبت قیمت با واحد متفاوت قبلاً در B نرمال می‌شود.
- Assembly و Scenario نسخه الگوریتم هزینه 2.0 منتشر می‌کنند. قفل پدر Assembly و Scenario و قفل مرتب مصالح، رقابت متداول را محدود می‌کند؛ deadlock احتمالی تراکنش بین چند Assembly باید rollback و توسط کاربر دوباره اجرا شود. هیچ retry خودکار نوشتن در این بسته نیست.
- V71: ارز CostEstimate از ۳ به ۸ کاراکتر؛ سه جدول فرزند که در V58 علامت scope نداشتند علامت‌گذاری ساختاری می‌شوند. پیش‌شرط migration: principal مجاز با امکان ممیزی همه scopeها. وجود رابطه legacy بین scopeهای متفاوت، migration را متوقف می‌کند؛ هیچ تغییر دسته‌بندی خودکار داده یا پاک‌کردن شواهد انجام نمی‌شود.
- V71 روی ۱۴ جدول مسیر مستقیم هزینه/سناریو/امکان‌سنجی/مالی FORCE RLS و policy محدودکننده متصل به تابع V69 ایجاد می‌کند. کنترل والد و نوع رابطه در Trigger؛ جدول‌های برآورد و Snapshot append-only و تراکنش انتشار شامل seal مؤخر است. شناسه تراکنش فقط برای پدرهای جدید تنظیم می‌شود؛ رکوردهای قدیمی باقی می‌مانند. تست واقعی PostgreSQL لازم است.
- Feasibility جدید تنها از Scenario snapshot نسخه 2.0 استفاده می‌کند و Financial Analysis جدید باید علامت materialCostAlgorithmVersion در snapshot امکان‌سنجی داشته باشد. گذشته فقط خوانده می‌شود. سازگاری کاربری: داده‌های قدیمی برای تصمیم جدید باید با قیمت مرجع تازه دوباره محاسبه شوند؛ محاسبات قدیمی به‌صورت خودکار تغییر نمی‌کنند.
- API مشترک Assembly/Scenario: GET با CASE_READ یا USER_MANAGE؛ POST/PUT/DELETE با USER_MANAGE. این تصمیم محافظه‌کارانه چون کاتالوگ عمومی است و به پرونده خصوصی واحد محدود نشده؛ ارزیابی اثر روی همه Callerها با HTTP ضروری است. TESTER مجوز مستقیم ندارد.
- منوی «بازار و همکاران → مصالح و قیمت ساخت» با پنج تب، فرم‌های کاتالوگ و Variant، انتخاب واحد و ارز مرجع و منبع فعال همان scope، ثبت دستی قیمت، تصمیم همراه دلیل، فیلتر، بازسازی و تاریخچه. همه عملیات به API واقعی و CSRF client موجود متصل‌اند. داده ساختگی یا seed نداریم. نقش فقط مشاهده فرم نوشتن ندارد. متن خام شواهد به‌صورت رشته حفظ می‌شود. برای جلوگیری از گردکردن ناخواسته JSON در مرورگر، ویرایش رکورد دارای metadata/attributes سفارشی از این فرم انجام نمی‌شود؛ از API تخصصی استفاده کنید. اعداد بزرگ صرفاً نمایشی و تقریبی‌اند؛ موتور مالی در Java است. تاریخ ثبت از زمان محلی دستگاه به ISO UTC تبدیل می‌شود.
- Runtime source policy برای V69–71 با حداقل نیازها و کنترل‌های schema نوشته شده، اما نقش واقعی و policy اپراتور به‌صورت خودکار تصویب/اعطا نمی‌شود. از فرآیند موجود role_lifecycle و migration_lifecycle با Clone، بازبینی تمام routineها/جدول‌ها و تأیید اپراتور استفاده شود. فایل سیاست نقش فیزیکی reviewed واقعی در دسترس نبود. runtime به TRUNCATE یا UPDATE/DELETE snapshot نیاز ندارد. به TEST workspace هیچ مجوز جدید اعطا نشده است.

## ماتریس پذیرش ۴۳ تا ۶۰
| فعالیت | وضعیت سورس | اثبات/مانع |
|---|---|---|
| 43 Resolver | پیاده‌سازی | تست مستقل و JUnit؛ DB lookup نیازمند اجرای واقعی |
| 44 Assembly integration | پیاده‌سازی | تست سرویس آماده؛ Maven مقصد |
| 45 Assembly validation | پیاده‌سازی | قفل و Decimal و کنترل نسخه/منطقه/ارز |
| 46 Scenario integration | پیاده‌سازی | تست زنجیره با repository mock؛ هنوز E2E نیست |
| 47 Snapshot integrity | پیاده‌سازی V71 | SQL clone و آزمون منفی واقعی لازم |
| 48 Feasibility | پیاده‌سازی gate نسخه | regression کامل و HTTP لازم |
| 49 Finance | پیاده‌سازی gate و lineage | regression کامل لازم |
| 50 Material UI | پیاده‌سازی | TSX syntax بررسی؛ build واقعی لازم |
| 51 Price UI | پیاده‌سازی | binding مبلغ اعشاری رشته‌ای و پاسخ‌ها نیازمند HTTP واقعی |
| 52 Review UI | پیاده‌سازی | RBAC و CSRF واقعی لازم |
| 53 Anomaly UI | پیاده‌سازی | browser واقعی لازم |
| 54 Dashboard | جدول قیمت جاری و آمار Backend | بدون تجمیع ارز/منطقه در مرورگر |
| 55 History | جدول تاریخچه و lineage | نمودار زمانی اختصاصی توسعه نیافته |
| 56 RTL UX | CSS responsive و فرم‌های فارسی | بازبینی انسانی UI لازم |
| 57 Quality fixtures | SQL rollback-only و تست سرویس | provisioning امن و ثبت در Quality Center هنوز BLOCKED |
| 58 Business E2E | قرارداد پذیرش | BLOCKED تا provision و ایزوله‌سازی کامل؛ PASS جعلی ممنوع |
| 59 Performance & regression | قفل و کنترل source | load test، pagination server-side و full regression جدید اجرا نشده |
| 60 Release | بسته و مستند | READY_TO_VERIFY؛ نه Production release |

## روش پذیرش
1. اجرای SelfTest/Audit/DryRun/ApplySource/Verify بسته؛ هیچ SQL یا Git push اجرا نمی‌شود.
2. `mvn --batch-mode --no-transfer-progress clean verify` در backend و `npm run build` در frontend با Node 24 و وابستگی‌های lock‌شده. CI پس از push شواهد جدید می‌دهد.
3. با ابزار Migration موجود روی Clone جداگانه: بررسی runtime source requirements برای همه نسخه‌های معلق، تولید/بازبینی صریح role policy و routineهای جدید، Flyway validate/migrate روی Clone؛ سپس با login واقعی `sakhtyar_runtime` ممیزی فقط‌خواندنی و بررسی privilege و RLS. اگر مالک قادر به دیدن همه scopeها نیست یا رابطه legacy ناسازگار است، متوقف شوید.
4. SQL آزمون `phase18_c_test_workspace_negative.sql` فقط روی Clone مصرف‌شدنی و login محدود `sakhtyar_tws_<uuid>` از قبل provision‌شده؛ نیازمند grants محدود صریح. کل اسکریپت rollback-only است؛ در نبود provisioning، BLOCKED. این تست SQL است نه تست HTTP/Business E2E. نقش App TESTER را با نقش PostgreSQL اشتباه نگیرید.
5. E2E پس از ایزوله‌سازی: TEST ثابت ۱٬۵۰۰٬۰۰۰ تومان برای ۵۰ KG → ۳۰٬۰۰۰ تومان/KG؛ ثبت مجدد همان quote؛ review؛ rebuild؛ BOM ۲ KG با ضریب پرت ۰٫۱۰ و مقدار Assembly برابر ۵ → ۱۱ KG → ۳۳۰٬۰۰۰ تومان. انتشار مرجع ۴۰٬۰۰۰ → محاسبه جدید ۴۴۰٬۰۰۰؛ بررسی عدم تغییر برآورد قبلی و FK. رد قیمت منقضی/نامعتبر و دسترسی cross-workspace. سناریوی جدید باید feasibility قانونی معتبر مطابق Gate فاز 17 داشته باشد؛ سپس مالی با همان ارز و lineage. هیچ ارتباط خارجی برقرار نشود. آزمون با دو login محدود و گزارش RLS واقعی لازم است؛ fixture مستقیم SQL جای سرویس‌های تجاری نیست.
6. UI فقط در محیط امن، با حساب read-only و admin و TESTER بررسی شود: عدم نمایش فرم نوشتن، خطای 403، خطای CSRF، empty/error، RTL، موبایل، فرم‌ها و history. اگر محیط ایزوله HTTP برای نقش TESTER فراهم نیست، Business E2E را اجرا نکنید.

## هشدارهای باقی‌مانده
- V71 به PostgreSQL واقعی اجرا نشده؛ تمام ۷۰ migration مبنا در CI موفق بوده‌اند ولی آن نتیجه به V71 تعمیم داده نمی‌شود.
- این بسته نه role bootstrap انجام می‌دهد نه کل اپلیکیشن را scope-safe اعلام می‌کند. ماژول‌های وابسته بیرون از مسیر مستقیم (مانند sensitivity و integrationهای دیگر) به ممیزی جداگانه نیاز دارند. RLS برای superuser/BYPASSRLS مرز امنیت نیست.
- صفحه همه رکوردهای بازگشتی APIهای موجود را دریافت می‌کند؛ pagination server-side و نمودار تاریخی مستقل هنوز اضافه نشده‌اند. اجرای full browser، load test و business E2E واقعی باقی است.
- پس از اعمال migration، فایل migration را حذف نکنید؛ rollback سورس فقط پیش از migration یا با برنامه سازگار DB مجاز است. هیچ automatic Flyway repair/drop/truncate در installer وجود ندارد.
