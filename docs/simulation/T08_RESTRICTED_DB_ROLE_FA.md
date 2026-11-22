# T08 | مانع فعلی اجرای واقعی E2E و نحوه رفع امن آن

**وضعیت ثبت‌شده از گزارش ارسالی:** هر ۲۴ سناریو وضعیت BLOCKED دارند. ۷۲ بررسی وجود PostgreSQL/جدول/API موفق بوده‌اند؛ درخواست HTTP کسب‌وکار و عملیات اصلی ۲۳ ماژول اجرا نشده است. برای E2E-14، PostgreSQL role فعلی `sakhtyar` با `SUPERUSER` یا `BYPASSRLS` شناسایی شده و گیت درست عمل می‌کند.

## یک تمایز حیاتی

- مرحله ۵ در T08، `DOMAIN_SCHEMA_SCOPE_AUDIT_NOT_BUSINESS_E2E`، پایگاه واقعی را بابت وجود ستون‌های `data_scope`, `test_workspace_id`، فعال‌بودن `RLS/FORCE RLS`، PK، بعضی FKهای کلیدی و وضعیت role می‌سنجد.
- موفقیت این **ممیزی ساختاری** به معنای اجرای API، ذخیره قرارداد، OCR، اعلان، Agent یا محاسبه مالی نیست.
- تمام ۲۳ سناریوی فاقد اجرای تجاری پس از ممیزی همچنان با وضعیت BLOCKED می‌مانند؛ این ویژگی عمدی است و نباید خاموش شود.
- این بسته Migration جدید، تغییر دسترسی یا داده تجاری ندارد.

## رفع گیت PostgreSQL: ابتدا در دیتابیس آزمایشی و با بک‌آپ

۱. از PostgreSQL بک‌آپ قابل بازیابی تهیه کنید. در psql یا pgAdmin با همان حساب **runtime** برنامه بررسی کنید:

```sql
SELECT current_user AS runtime_user, rolsuper, rolbypassrls
FROM pg_roles WHERE rolname = current_user;

SELECT n.nspname, c.relname, c.relrowsecurity, c.relforcerowsecurity
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public'
  AND c.relname IN ('construction_case','property','property_owner','case_document')
ORDER BY c.relname;
```

۲. حساب فوق‌مجاز `sakhtyar` را برای اتصال روزمره استفاده نکنید. یک حساب **مستقل و محدود** مثلا `sakhtyar_runtime` را توسط مدیر دیتابیس ایجاد کنید؛ دستورالعمل آماده در `T08_RESTRICTED_ROLE_TEMPLATE.sql` وجود دارد. **نقش مالک/مهاجرت Flyway** را جدا نگه دارید تا Migrationها بتوانند با مجوز مناسب اجرا شوند.

۳. اطلاعات کاربری runtime برنامه را از **متغیرهای محیطی خارج از Git** تنظیم کنید:

- `SPRING_DATASOURCE_USERNAME=sakhtyar_runtime`
- `SPRING_DATASOURCE_PASSWORD=...` (رمز واقعی فقط در secret store)
- برای مهاجرت در صورت نیاز `SPRING_FLYWAY_USER` و `SPRING_FLYWAY_PASSWORD` را به‌صورت جدا و امن تنظیم کنید.

۴. **قبل از اعمال به محیط اصلی** بررسی کنید نقش جدید به جداول موردنیاز، sequenceها و schema حق دسترسی موردنیاز را داشته باشد؛ و درعین‌حال `rolsuper=false` و `rolbypassrls=false` باشد. سپس اجرای چهارجدولی E2E-14 را روی دیتابیس stage تست کنید.

۵. **نکته:** انتقال به نقش محدود تنها شرط ابتدایی است؛ باقی ماژول‌ها هنوز به RLS و فیلتر workspace، قیود FK بین Scopeها، و محافظ MinIO/Redis/notification/crawler/AI نیاز دارند.

## خروجی جدید در صفحه

در هر اجرای جدید با تمام سناریوها، در کنار ۴ مرحله قبلی یک گام خواندنی اضافی برای ۲۳ سناریو ثبت می‌شود:

- جداول فاقد برچسب TEST و workspace
- جداول بدون RLS و FORCE RLS
- جداول فاقد Primary Key
- برخی روابط کلیدی فاقد Foreign Key
- نقش runtime با مجوز نامناسب
- نتیجه جداگانه سلامت Flyway

نتایج در Modal «مشاهده نتیجه» و فایل JSON قابل مشاهده‌اند. درصد اتمام مراحل را با درصد قبولی گردش‌کار تجاری اشتباه نگیرید.

## پوشش واقعی

✅ کد **۲۳ ممیزی دامنه واقعی و فقط‌خواندنی** + آزمون خودکار آن‌ها.

⛔ هنوز **۲۳ گردش‌کار کامل E2E** ایجاد/تغییر/بازیابی/ارزیابی کسب‌وکار قابل اجرا نیست. هیچ اثر جانبی خارجی یا داده تجاری بدون فراهم‌بودن جداسازی گسترده مجاز نیست. برای تکمیل E2E واقعی باید در هر ماژول مجزا Fixture+Action+Assertion ساخته شود و روی stage با PostgreSQL ایزوله تأیید شود.
