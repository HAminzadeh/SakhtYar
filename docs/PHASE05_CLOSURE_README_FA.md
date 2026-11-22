# ساخت‌یار | Phase 05 Closure: Construction / Finance Foundation

## تحویل

- ZIP شامل تغییرات Source و تست‌ها، Golden مستقل، Manifest مسیر/SHA256، Gap Matrix و Handoff است.
- لانچر مستقل `Apply-SakhtYar-Phase05-Closure.ps1` با PowerShell 5.1؛ ZIP را دستی Extract نکنید.
- مبنا `v2` و SHA `831a54ec612166f825cc8fc87159d67d3b2b9247` در زمان ساخت بسته؛ SHA فقط برای Source فایل‌های هدف استفاده می‌شود و تغییر Commit نامرتبط مانع نصب نیست.
- **هیچ Clone، SQL، Migration، Restore، Seed یا پاک‌سازی دیتابیس توسط بسته اجرا نمی‌شود.**

## اجرا

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Apply-SakhtYar-Phase05-Closure.ps1 -Mode Audit
powershell -NoProfile -ExecutionPolicy Bypass -File .\Apply-SakhtYar-Phase05-Closure.ps1 -Mode DryRun
powershell -NoProfile -ExecutionPolicy Bypass -File .\Apply-SakhtYar-Phase05-Closure.ps1 -Mode ApplySource
powershell -NoProfile -ExecutionPolicy Bypass -File .\Apply-SakhtYar-Phase05-Closure.ps1 -Mode Verify -RunBuild
```

حالت پیش‌فرض `ApplySource` است؛ به‌صورت خودکار پس از نصب Python Oracle، JUnit انتخابی و `mvn ... -DskipTests package` را تلاش می‌کند. `-SkipBuild` برای نصب Source بدون Build است. اجرای مجدد پس از شکست Maven فقط تست‌ها را ادامه می‌دهد؛ Source بدون تغییر باقی می‌ماند.

## Guardهای Installer

- فایل هدف باید همان Git Blob مبنا را داشته باشد یا با بازگرداندن همه عملیات معکوس، دقیقاً به Blob مبنا برسد.
- فایل جدید فقط در صورت عدم وجود یا Hash یکسان مجاز است. فایل Stage‌شده یا ناسازگار رد می‌شود.
- قبل از نوشتن روی فایل‌ها Backup جداگانه ایجاد می‌شود؛ خطای نصب باعث Rollback تغییرات همان اجرای اسکریپت می‌شود؛ شکست Build بعد از نصب Source را Rollback نمی‌کند.
- هیچ `reset`, `clean -fd`, `push`, یا `commit` اجرا نمی‌شود.
- SHA256 همه فایل‌های ZIP کنترل و خروجی JSON در کنار لانچر نوشته می‌شود.

## Golden مستقل و قرارداد محاسبات

- واحد پول `IRR` یا `TOMAN` *صریح* است. هیچ تبدیل پنهان بین ریال و تومان رخ نمی‌دهد. Golden فقط IRR مصنوعی است.
- `HALF_UP` با Scale پولی ۲ و Scale نسبت ۶؛ ترتیب تخصیص بر اساس درصد سهم و سپس کلید قطعی است، اما ترتیب خروجی API مانند ورودی باقی می‌ماند.
- `snapshot` عددی immutable همراه Frozen Clock و `inputHash`/`outputHash`/`envelopeHash` است. در این فاز در DB ذخیره نمی‌شود.
- `Phase05SyntheticFixtureManifest` شناسه‌ها را مشخص می‌کند ولی Seed و Cleanup زنده را عمداً `BLOCKED` می‌کند.

## آزمون‌هایی که باید محلی اجرا شوند

- `python tests/phase05/verify_phase05_oracle.py`؛ مستقل از کد Java.
- اجرای Maven در `backend` با `-pl modules/finance,modules/material,modules/analysis,modules/assembly,modules/quality -am` و فیلتر تست‌های انتخابی؛ فرمان دقیق در گزارش لانچر.
- Maven در صورت نبود `mvnw.cmd/mvn.cmd` یا نصب IntelliJ به‌صورت `NOT_RUN` گزارش می‌شود. نصب از طریق Maven Embedded اگر موجود باشد تشخیص داده می‌شود.
- `npm run build` اختیاری برای Frontend موجود و بدون `npm ci` (پکیج Security در فاز جداگانه Commit شده است).

## محدودیت‌ها

تست‌های JUnit، Integration PostgreSQL، UI، Restore/Crash، Storage و ۲۴ سناریوی Business E2E در محیط سازنده بسته قابل اجرا نیستند. هیچ‌یک را به‌صورت ساختگی PASS گزارش نکنید. بررسی ACL به‌صورت Unit کافی نیست؛ HTTP و تست دو کاربر/دو پرونده باید جدا اجرا شوند.
