# ساخت‌یار | بسته تکمیلی فاز ۰۴ — پردازش دانش، OCR و خزشگر

## مبنای قطعی و حدود اجرا
- مخزن `HAminzadeh/SakhtYar`، شاخه `v2`، مبنای Patch: `c2537a043d9f71ab92e28b3d8b69479ee3a62525` (۷ نوامبر ۲۰۲۶).
- Package فقط Patch مربوط به ۶ فایل موجود، ۵ کلاس Java جدید، ۶ فایل JUnit و قراردادهای آفلاین/گزارش‌ها را می‌آورد.
- **عدم اتصال به دیتابیس:** لانچر هیچ Clone/DDL/Migration/Seed/پاک‌سازی روی PostgreSQL نمی‌سازد یا اجرا نمی‌کند؛ هیچ سرویس Spring Boot را Start نمی‌کند.
- REAL/TEST در دیتابیس مشترک باقی می‌مانند. آزمون‌های زنده نیازمند شواهد مستقل، Principal محدود، مجوز و داده مصنوعی هستند.
- هیچ Commit/Push از طریق لانچر انجام نمی‌شود.

## فایل‌های تحویل
1. `SakhtYar-Phase04-Closure.zip`: Patch، Java/JUnit، Manifest SHA-256، قراردادهای Node، Gap Matrix، Handoff و متن Commit.
2. `Apply-SakhtYar-Phase04-Closure.ps1`: لانچر مستقل Windows PowerShell 5.1؛ برای اجرای مستقیم داخل Downloads.
3. بسته Delivery (اختیاری): شامل هر دو فایل بالاست.

## فرمان مستقیم (بدون Extract کردن ZIP اصلی)
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Apply-SakhtYar-Phase04-Closure.ps1 -Mode Audit
powershell -NoProfile -ExecutionPolicy Bypass -File .\Apply-SakhtYar-Phase04-Closure.ps1 -Mode DryRun
powershell -NoProfile -ExecutionPolicy Bypass -File .\Apply-SakhtYar-Phase04-Closure.ps1 -Mode ApplySource
powershell -NoProfile -ExecutionPolicy Bypass -File .\Apply-SakhtYar-Phase04-Closure.ps1 -Mode Verify -RunBuild
```

`ApplySource` به‌طور پیش‌فرض تست‌های آفلاین و Build را نیز اجرا می‌کند؛ برای نصب بدون Build از `-SkipBuild` استفاده کنید. در صورت اجرای مجدد، Patch دوباره روی فایل اصلاح‌شده اعمال نمی‌شود. مسیر گزارش `Phase04-Closure-Report.json` در کنار لانچر است.

## محافظ‌های امنیتی لانچر
- قبل از تغییر فایل، SHA256 فایل ZIP و تمام Manifest و Git Blob تک‌تک ۶ فایل قدیمی بررسی می‌شود.
- HEAD دقیق و Branch `v2` باید با Manifest یکی باشند؛ هر مغایرت محلی در فایل هدف موجب توقف می‌شود.
- `git apply --check` همه Hunkها را یکجا بدون نوشتن بررسی می‌کند؛ وابسته به Anchorهای شکننده‌ی PowerShell نیست.
- قبل از اعمال Patch، فایل‌های موجود در `SakhtYar-PatchBackups` خارج از Repo Backup می‌شوند. اگر مرحله نصب شکست بخورد، فایل‌های همین عملیات Restore و فایل‌های تازه‌ساخته‌شده این عملیات حذف می‌شوند.
- لینک‌های نمادین/Junction در مسیر فایل هدف پذیرفته نمی‌شوند؛ خارج از مسیر Repo هیچ فایلی با Pathِ بسته نصب نمی‌شود.
- در اجرای تکراری، صحت فایل‌های افزوده و قابلیت reverse-check Patch بررسی می‌شود؛ هر اختلاف به `BLOCKED` می‌رود.

## اصلاحات واقعی Source
- `KnowledgeDatasetPackageService`: کپی ZIP از MinIO با سقف بایت، بررسی کامل محتوا/CRC/نام فایل/فشرده‌سازی پیش از Extract، پاک‌سازی Temp هنگام شکست، رد تکرار Path و Hash خراب در Manifest.
- `IngestionPipeline`: احراز Principal واقعی و `USER_MANAGE`، دریافت و کنترل robots.txt قبل از ایجاد Job، جلوگیری از خزش URL غیرمجاز.
- `SecurityConfig`: محدودشدن `/api/v1/crawler/**` به `USER_MANAGE` برای کنترل در مرز HTTP.
- `UrlDiscovery` و `UrlCanonicalizer`: الزام allowlist و HTTP(S) syntax؛ حذف رفتار fail-open.
- `PageFetcher`: محدودسازی MIME در پاسخ موفق.

## قراردادهای مصنوعی و موارد عمداً باز
- OCR fake متن کنترل‌شده و Hash را بازمی‌گرداند؛ **OCR واقعی اجرا نشده است**.
- Review/Checkpoint و Retrieval به‌صورت in-memory و غیر متصل به DB طراحی شده‌اند؛ Crash/Resume واقعی و تضاد هم‌زمان DB اثبات نشده است.
- Robots parser برای REAL crawler است و PageFetcher همچنان real HTTP می‌زند؛ شبکه TEST باید جداگانه در زیرساخت بسته شود. DNS rebinding نیازمند Firewall/Network Policy است.
- تست منفی خارج از Worktree و منع egress زیرساخت هنوز باید روی محیط واقعی مستقل اثبات شود.
- هشت سناریوی دانش E2E-04 و E2E-07..13 همچنان Business BLOCKED هستند. قبولی Unit یا Build جایگزین E2E نیست.

## آزمون‌های Windows که لانچر اجرا می‌کند
- `node tests/phase04/check_phase04_source_contract.mjs` (SOURCE_ONLY)
- `node tests/e2e/check_24_plans.mjs tests/e2e/scenario-plans.json`
- `mvn.cmd -pl modules/knowledge,modules/crawler -am -Dtest=Phase04ArchiveGuardTest,Phase04SyntheticContractsTest,Phase04RobotsPolicyTest,Phase04CrawlerBoundaryTest,Phase04PageFetcherMimeTest,Phase04CrawlerOperatorBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`
- `mvn.cmd -DskipTests package` (Build همه ۳۳ ماژول، بدون اجرای تست‌ها؛ اگر ساختار Reactor بدون تغییر باشد)
- `npm ci` و `npm run build` در Frontend.

## Rollback و Handoff
- قبل از Rollback هر فرآیند Spring مرتبط را متوقف کنید، نتیجه Checksum و فایل Backup را با گزارش JSON تطبیق دهید.
- لانچر نه `git reset --hard`، نه `git clean`، نه `git push` اجرا می‌کند. Backupها خودکار حذف نمی‌شوند.
- چون Patch هنوز Commit نشده است، Build محلی باید provenance را `UNVERIFIED` ثبت کند؛ SHA واقعی پس از Commit و CI بررسی شود.
- برای فاز بعد `tests/phase04/HANDOFF_PHASE05.md` و `docs/PHASE04_GAP_MATRIX_FA.md` را بخوانید.

## اصلاح بسته Hotfix v2 ـ جلوگیری از تداخل فایل موجود در Git
- در HEAD مبنا، `docs/PHASE04_COMMIT_EN.md` **از قبل ثبت شده است** و لانچر پیشین به‌اشتباه می‌خواست آن را به‌عنوان فایل جدید نصب کند.
- در این بسته متن Commit مربوط به Closure در `docs/PHASE04_CLOSURE_COMMIT_EN.md` نصب می‌شود. فایل قدیمی حفظ می‌شود.
- لانچر اگر هر مسیر جدید در manifest از قبل در index گیت Tracking شده باشد، پیش از هر تغییر متوقف می‌شود.
- گزارش جدید: `Phase04-Closure-Hotfix-v2-Report.json`. فایل ZIP و لانچر نسخه قبل را کنار بگذارید.
