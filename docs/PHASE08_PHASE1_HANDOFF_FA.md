# ساخت‌یار — Handoff فاز ۰۱ از برنامه سه‌فازی E2E

مبنا: `v2` / `860670dd558c5c009d52d4408382a30c16ac76d6`، گزارش تشخیصی `79b7fc27-dbed-4bb9-b73e-e71bee350c87`، ۸ نوامبر ۲۰۲۶.

## مرز دستاورد واقعی

این بسته **گیت fail-closed قبل از هر نوشتن در Business E2E `/runs`**، API خواندنی و مبتنی بر مالک Workspace برای ارزیابی شواهد ایزولاسیون، قراردادهای شواهد ۷ سناریوی فاز اول، Blueprint مصنوعی و Deterministic برای پرونده ۳۰۰ متر و سهم ۶۰/۴۰ و مجموعه تست‌های بدون دیتابیس را اضافه می‌کند. تب تشخیصی فاز ۰۷ و موتور اصلی REAL/TEST حفظ شده‌اند.

**این بسته شواهد لازم برای اجرای واقعی گردش‌کارهای ۷ سناریو را جعل نمی‌کند و حتی E2E-14 را با اتصال Backend عادی فعال نمی‌کند.** وضعیت درست تا اثبات نقش محدود فیزیکی، پوشش همه Queryها، ارسال‌های خارجی، Cleanup و Artifact Store همچنان BLOCKED است. نوشتن رکورد اجرای Business E2E و ایجاد خودکار `tester` هم وقتی گیت بسته است، آغاز نمی‌شود. گزارش `/safety-readiness` بدون Mutation قابل دریافت است.

## چک‌لیست پیش‌نیاز بازکردن گیت (هر کدام شواهد مستقل می‌خواهد)

1. Workspace TEST فعال و مالکیت `USER_MANAGE`؛ هر نوع عدم تطابق، HTTP 403/404.
2. اتصال فیزیکی PostgreSQL با `current_user=session_user=sakhtyar_tws_<workspaceId>` و `row_security=on`، نه `SET ROLE` روی اتصال پرامتیاز.
3. `Phase02RestrictedIsolationProbe` با نتیجه مستند `CORE_READ_PROBE_OBSERVED` و baseline مثبتِ Workspace دیگر، همراه با عدم مشاهده ردیف‌های REAL.
4. پوشش RLS و Provenance در تمام جدول‌های گردش‌کار مربوط؛ سیاست جداگانه برای جدول‌های Global/Identity/Config (بدون افزودن کور RLS).
5. محدودسازی تمام اتصالات HTTP، ایمیل، SMS، Webhook، AI، MinIO و Redis به Fake یا Mock امن.
6. Cleanup مبتنی بر `workspaceId` + `runId` با شواهد دقیق عدم دسترسی به سایر منابع.
7. Storage واقعی Evidence با SHA-256 بایت‌ها و امضای مستقل CI؛ محافظت از Token/Cookie/Password.
8. غیرفعال بودن Seed، Repo Commit قابل‌تأیید پس از Commit و Build مجدد.
9. راه‌اندازی نشده بودن DDL، Role Bootstrap، Migration، Restore یا Docker توسط نصب‌کننده.

## Gap Matrix — هفت سناریوی هدف

| سناریو | تشخیص مبنا | اجرای تجاری کامل | کار باز با شواهد حقیقی |
|---|---|---|---|
| E2E-01 | 4/6 | BLOCKED | Login/Logout/Expire/Revocation با Principal محدود و HTTP |
| E2E-02 | 4/6 | BLOCKED | تست ACL/IDOR با دو Principal و TEST case واقعی |
| E2E-03 | 4/6 | BLOCKED | HTTP admin/non-admin و 403 و Audit |
| E2E-04 | 4/6 | BLOCKED | Draft/Review/Reject TEST و منع نشر REAL |
| E2E-05 | 5/6 | BLOCKED | Flyway و Backup read-only؛ Restore عملی تحت ممنوعیت Clone قابل ادعای PASS نیست |
| E2E-06 | 4/6 | BLOCKED | Cookie/CSRF معتبر و منفی با HTTP واقعی |
| E2E-14 | 4/6 | BLOCKED | Fixture چهارجدولی Role محدود، ۶۰/۴۰، ACL و REAL-invisibility |

**جمع فاز:** ۲۹/۴۲ کنترل تشخیصی از گزارش مبنا، ۰/۷ Business E2E. این اعداد تا دریافت گزارش Runtime جدید تغییر نمی‌کنند. Source SHA پس از نصب با تغییرات Commit‌نشده عمداً `UNVERIFIED` می‌ماند؛ فقط Commit و Build از درخت clean حق تولید SHA معتبر دارند.

## مسیرهای کلیدی جدید

- `GET /api/v1/admin/simulations/e2e/safety-readiness?workspaceId=<uuid>`: فقط خواندنی، نیازمند مدیر و مالک Workspace.
- `POST /api/v1/admin/simulations/e2e/runs`: اکنون **قبل از ایجاد هر Run/Tester** گیت `Phase08SafetyAdmission` را ارزیابی می‌کند. در صورت نبود شواهد، HTTP 409 می‌دهد؛ عملیات دیتابیس تجاری انجام نمی‌شود.
- `SimulationCenter → اجرای واقعی E2E`: نمایش لیست دلایل BLOCKED و دانلود JSON گیت؛ دکمه‌های شروع تا عبور گیت غیرفعال‌اند.

## محدودیت‌های نصب و گزارش‌دهی

- ZIP و PowerShell فقط فایل‌های سورس را تغییر می‌دهند؛ هیچ DB Clone/SQL/Migration/Role Bootstrap/Seed/Docker/Git Write ندارند.
- اسکریپت نصب در حالت `Audit`، `Apply`، `Verify` و `ApplyAndTest` قابل‌اجراست؛ روی فایل tracked تغییرکرده یا Stage‌شده و فایل جدید متعارض Fail Closed است.
- JUnitهای جدید Mock و Pure Java هستند؛ تست Workflow واقعی نیازمند شواهد Runtime و مجوز TEST است. BUILD SUCCESS به معنی Business E2E PASS نیست.
- فایل‌های خروجی PowerShell برای QA باید همراه با Log و JSON گزارش شوند؛ انتشار Commit و Push فقط توسط کاربر انجام شود.
