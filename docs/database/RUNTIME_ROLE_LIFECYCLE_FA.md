# ساخت‌یار | مدیریت دائمی نقش و مجوز دیتابیس

مبنای طراحی: شاخه v2، HEAD مشاهده‌شده b275348efa82eb172a0686831b4bdd92e75098b5؛ سورس بسته Migration Lifecycle قبلی مبنای patch است. گزارش کاربر: 192 جدول متعلق به sakhtyar، فقط یک login محدود Test Workspace، بدون default ACL. V68 pending. هیچ SQL روی سیستم کاربر از طریق نصب‌کننده اجرا نمی‌شود.

## ۱. رفع مانع Clone

Restore آرشیو و تطابق تاریخچه و inventory قبلاً PASS بوده، هش Schema FAIL. در نسخه جدید Clone، فایل‌های `source-schema.private.sql`، `clone-schema.private.sql` و `schema-diff.private.txt` در همان مسیر report.json فقط در صورت اختلاف نوشته می‌شوند. مقایسه هنوز سخت‌گیرانه است؛ تنها جفت marker تصادفی pg_dump در ابتدا/انتها حذف می‌شود. علت واقعی تفاوت بدون diff قابل تأیید نیست. خروجی ممکن است شامل تعریف تابع/ACL/اطلاعات حساس باشد؛ آن را بررسی و فقط بخش غیرحساس لازم را ارسال کنید. Clone بعد از خطا حذف می‌شود. هرگز با ignore-drift یا repair عبور نکنید. `restore_drill: PASS` فقط Restore و inventory است، نه تطابق Schema.

## ۲. اجرا

از PowerShell 5.1 در root پروژه، Backend و سایر نویسنده‌ها متوقف باشند. ابتدا نصب سورس و SelfTest انجام شود. مسیر `scripts/database/Invoke-SakhtYar-DatabaseRoleLifecycle.ps1`:

- `SelfTest`: تست آفلاین.
- `Audit`: catalog نقش، جدول، view، sequence، routine، ACL پیش‌فرض، RLS و object owner. هیچ تغییر دیتابیس.
- `DraftPolicy`: تولید `runtime_privileges.DRAFT.json` و `source-reference-hints.json` خارج Git. پیشنهادهای migration حداقل هستند؛ نه ماتریس کامل. فایل catalog و hints فاقد ردیف داده تجاری‌اند؛ با این حال قبل از اشتراک بررسی شوند.
- `DraftClone`: Restore و اعمال migration فقط روی clone برای شناسایی همه objectهای جدید (از جمله توابع). تا رفع fingerprint مسدود است. draft شامل pending_objects و pending_versions است.
- `Plan`: نیازمند فایل بازبینی‌شده و committed در `scripts/database/runtime_privileges.reviewed.json`. توکن از HEAD، هش policy و catalog ساخته می‌شود.
- `DryRun`: Backup + Restore + اعمال policy روی clone + login واقعی + rehearsal migration و گیت‌های V68. هیچ CREATE ROLE/GRANT روی اصلی انجام نمی‌شود.
- `ApplyRole -Approve <token>`: اجرای مجدد clone و کنترل drift و approval؛ سپس ایجاد/تنظیم فقط sakhtyar_runtime روی اصلی. برای role جدید password با prompt تعاملی دو بار و بدون echo، حداقل 24 کاراکتر؛ رمز را به Git/خط فرمان/گزارش ندهید. تنظیم log_statement سرور باید توسط DBA بازبینی شود. Role موجود امن بدون reset رمز استفاده می‌شود. عملیات DDL و ACL در transaction است؛ نقص catalog/ACL باعث توقف می‌شود. هیچ restore خودکار روی اصلی وجود ندارد.
- `Verify`: بررسی effective privilege (شامل PUBLIC) روی role؛ نه اثبات login واقعی IDE یا E2E.

## ۳. ماتریس policy

تمام objectهای non-extension در schema public باید `ALLOW` یا `DENY` با reason صریح داشته باشند. `reviewed=true` فقط بعد از بازبینی سورس، dependencyها و SQLهای واقعی. برای هر جدول، SELECT/INSERT/UPDATE/DELETE فقط حسب نیاز؛ viewها نیز در inventory هستند. Sequence: USAGE/SELECT/UPDATE حسب نیاز؛ routine: EXECUTE. Flyway history فقط SELECT. Ledgerهای شناخته‌شده و triggerهای immutable/revision_guard حق UPDATE/DELETE ندارند. TRUNCATE/REFERENCES/TRIGGER/MAINTAIN برای runtime مجاز نیست. هیچ GRANT عمومی، DEFAULT PRIVILEGE عمومی یا REVOKE FROM PUBLIC خودکار انجام نمی‌شود. دسترسی PUBLIC ناسازگار، ACL ستونی و security-definer نیازمند DBA هستند و gate توقف می‌کند. Objectهای extension فقط inventory، نیازمند بررسی مستقل. این کد ممیزی کامل تمام schemaها یا همه مسیرهای privilege escalation نیست. هیچ role test workspace یا admin تغییر نمی‌کند. GUC scope به تنهایی مرز هویت نیست؛ isolation کامل هنوز اثبات نشده.

فایل DRAFT را به عنوان reviewed ثبت نکنید تا همه 192 جدول و سایر objectها بررسی شوند. `source-reference-hints.json` صرفاً تطابق tokenهای نام در Java است، ORM mappings، native query، call graph یا مجوز موردنیاز را اثبات نمی‌کند. عملیات مربوط به provision حساب‌های TEST که نیاز به DBA دارند باید جداگانه بررسی شوند؛ به runtime هرگز CREATEROLE داده نمی‌شود.

## ۴. یکپارچگی Migration

Migration Lifecycle برای DryRun/ApplyDatabase از role اختصاصی و policy بازبینی‌شده استفاده می‌کند. ابتدا ACLهای موجود verify، سپس migration روی clone اجرا و فقط مجوزهای pending_objects ثبت‌شده (مطابق catalog و rule migration) اعمال می‌شوند. همان مرحله پس از migration اصلی با تایید قبلی تکرار می‌شود. SQL migration و GRANT از دو transaction مستقل استفاده می‌کنند؛ اگر پس از migration اصلی grant شکست خورد، Backend را متوقف نگه دارید، report را بررسی کنید؛ rerun/repair/restore خودکار ممنوع. ApplyRole روی اصلی migration اجرا نمی‌کند؛ ApplyDatabase مجزا و تایید جداگانه دارد. برای هر نسخه آینده، ابتدا draft clone جدید و review policy و migration_runtime_policy.json لازم است. Verify بعد از ApplyDatabase شامل بررسی ACL و schema history است. فایل SQL تاریخی تغییر نمی‌کند. `APP_DEV_SEED_ENABLED` و Hibernate validate و Flyway خاموش پروفایل‌ها دست‌نخورده‌اند.

## ۵. IntelliJ

بعد از ApplyRole و ApplyDatabase موفق، در Run Configuration متغیر `SAKHTYAR_RUNTIME_DB_USER=sakhtyar_runtime`، `SAKHTYAR_RUNTIME_DB_PASSWORD` با secret واقعی خارج Git و `APP_T10_REQUIRE_RESTRICTED_RUNTIME=true` قرار گیرد. credential migration جدا (SAKHTYAR_FLYWAY_USER/PASSWORD). برای production همین اصل؛ ابزار فعلی فقط Docker local را قبول می‌کند و روی سرور اجرا نمی‌شود. پروفایل‌های موجود تغییری نکرده‌اند؛ در صورت نیاز provisioning عملیاتی با DBA انجام شود. اجرای Maven/Backend و UI و تست منفی/مثبت پس از تایید role باید جداگانه اثبات شود.

## ۶. وضعیت

READY_TO_VERIFY؛ تست آفلاین 37 مورد. Docker/PostgreSQL/PowerShell واقعی در محیط تولید بسته موجود نیست. Clone fingerprint همچنان gate است. ماتریس کامل privilege و runtime role واقعی هنوز ساخته یا تایید نشده است. نصب‌کننده فقط سورس، backup و rollback سورس دارد؛ Rollback ACL/DB نیازمند DBA و backup معتبر است.

## ۷. بسته Resume پس از Push
دو فایل پایه با Git blobهای آخرین HEAD مطابقت داده شده‌اند؛ نصب فقط در صورت نبود تغییرات محلی روی هدف‌ها انجام می‌شود. اختلاف Schema هنوز به diff واقعی نیاز دارد. DraftClone در صورت موفقیت، `clone-migrated-catalog.json` را نیز ثبت می‌کند. برای role جدید، رمز ۲۴ کاراکتر یا بیشتر از ASCII چاپی بدون فاصله انتخاب شود. SQL ساخت حساب به جای متن رمز، verifier SCRAM را دریافت می‌کند؛ verifier نیز محرمانه است. آزمون TCP login کلون لزوماً الزام password در pg_hba را اثبات نمی‌کند.
