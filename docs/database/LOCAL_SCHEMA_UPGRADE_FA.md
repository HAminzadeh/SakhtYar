# ارتقای صریح Schema محیط محلی (V68)

این ابزار مسیر جداگانه و محدود برای نصب Schema بررسی‌شده V68 در PostgreSQL محلی است؛ معادل تأیید امنیتی Runtime یا Production نیست. مسیر استاندارد ApplyRole / ApplyDatabase بدون تغییر باقی مانده است. دیتابیس محلی مشترک REAL/TEST است؛ برچسب local به معنی خالی یا غیرحساس بودن داده نیست.

- فقط شاخه v2، Docker context محلی، کانتینر مورد انتظار و SQL دقیق V68 با SHA256 ثبت‌شده پذیرفته می‌شوند. نسخه بعدی باید جداگانه بازبینی شود.
- Plan فقط خواندنی است. Apply به توکن جدید وابسته به HEAD، کانتینر، تاریخچه، Schema، Role و Migration نیاز دارد.
- Apply ابتدا Backup جدید و Restore ایزوله، Flyway validate/migrate، کنترل جدول‌ها، RLS/FORCE RLS، Trigger و Policy و نبود PUBLIC grant را روی Clone انجام می‌دهد. فقط در صورت تطابق و نبود تغییر مقصد و نبود اتصال دیگر، V68 را روی دیتابیس اصلی محلی اجرا می‌کند.
- از Image موجود Flyway و PostgreSQL استفاده می‌شود؛ هیچ Docker Build یا Pull مربوط به Backend انجام نمی‌شود. هیچ GRANT یا CREATE ROLE روی مقصد اجرا نمی‌شود و هیچ داده تجاری مستقیماً تغییر داده نمی‌شود. V68 افزایشی است.
- در صورت وجود sakhtyar_runtime یا Runtime جایگزین تنظیم‌شده، Apply متوقف می‌شود؛ از مسیر استاندارد با Policy تأییدشده استفاده کنید.
- پس از Verify، Startup از IntelliJ با تنظیمات موجود و Flyway خاموش/Hibernate validate قابل آزمایش است. اگر اتصال موجود مالک/SUPERUSER باشد، نتیجه فقط بررسی راه‌اندازی است، نه مجوز انجام عملیات تجاری، E2E یا Production. Runtime محدود باید جداگانه تکمیل و تأیید شود. هیچ گیت برنامه را غیرفعال نکنید.
- در صورت خطا پس از STARTED، عملیات را کورکورانه تکرار نکنید؛ report.json، تاریخچه و وضعیت واقعی Schema را بررسی کنید. بازیابی خودکار انجام نمی‌شود.

```powershell
$tool = '.\scripts\database\Invoke-SakhtYar-LocalSchemaUpgrade.ps1'
powershell -NoProfile -ExecutionPolicy Bypass -File $tool -Mode SelfTest
powershell -NoProfile -ExecutionPolicy Bypass -File $tool -Mode Plan
# پس از بازبینی Plan، توکن چاپ‌شده را دقیقاً وارد کنید:
powershell -NoProfile -ExecutionPolicy Bypass -File $tool -Mode Apply -Approve '<current-token>'
powershell -NoProfile -ExecutionPolicy Bypass -File $tool -Mode Verify
```
