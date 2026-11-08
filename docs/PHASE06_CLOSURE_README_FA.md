# راهنمای فاز ۰۶ ساخت‌یار — بدون Clone و بدون عملیات دیتابیس

## نصب OneClick

فقط دو فایل جدید را داخل `Downloads` بگذارید:

- `SakhtYar-Phase06-Closure-20261108.zip`
- `Apply-SakhtYar-Phase06-Closure.ps1`

از همان پوشه PowerShell 5.1 دستور زیر را اجرا کنید:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Apply-SakhtYar-Phase06-Closure.ps1
```

لانچر بدون Extract دستی، Hash ZIP و فایل‌ها، Manifest، شاخه Git و Blobهای فایل‌های هدف را بررسی می‌کند. از فایل‌های تغییرپذیر Backup گرفته و تنها فایل‌های هم‌خوان را اعمال می‌کند. اجرای مجدد در صورت تطبیق دقیق فایل‌ها هیچ چیزی را بازنویسی نمی‌کند.

حالت‌ها:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Apply-SakhtYar-Phase06-Closure.ps1 -Mode Audit
powershell -NoProfile -ExecutionPolicy Bypass -File .\Apply-SakhtYar-Phase06-Closure.ps1 -Mode DryRun
powershell -NoProfile -ExecutionPolicy Bypass -File .\Apply-SakhtYar-Phase06-Closure.ps1 -Mode Verify -RunBuild
powershell -NoProfile -ExecutionPolicy Bypass -File .\Apply-SakhtYar-Phase06-Closure.ps1 -Mode ApplySource -SkipBuild
```

گزارش: `SakhtYar-Phase06-Closure-Report.json` کنار لانچر، همراه Logهای مرحله‌ای. **هیچ Clone، SQL، Seed، Migration، Role/RLS، Restore، Cleanup، Docker، Reset، Commit یا Push توسط لانچر انجام نمی‌شود.**

## تست‌های بدون دیتابیس

```text
node tests/phase06/release_gate.contract.test.mjs
node tests/phase06/proof_verifier.contract.test.mjs
node tests/phase06/source_contract.mjs
node tests/e2e/check_24_plans.mjs tests/e2e/scenario-plans.json
```

لانچر Maven را از `backend/mvnw.cmd`، سپس `mvn.cmd`، `MAVEN_HOME/M2_HOME` و Maven نصب‌شده IntelliJ شناسایی می‌کند. سپس JUnit انتخابی فاز ۰۶ و Backend `-DskipTests package` و `npm run build` را اجرا می‌کند. این آزمایش‌ها به دیتابیس متصل نمی‌شوند؛ Maven و npm ممکن است برای Dependencyها از Registry عادی استفاده کنند ولی خروجی TEST ابزارها از Providerهای REAL قطع است.

Playwright: مسیر `tests/phase06/browser` حاوی آزمون‌های مستقل، بدون Login REAL یا انتقال Cookie است. CI برای Synthetic از `npm install`, `playwright install --with-deps chromium`, سپس `npm test -- --grep 'synthetic two-workspace'` استفاده می‌کند. اگر Browser در ویندوز نصب نباشد، نتیجه `NOT_RUN` می‌ماند.

## CI و Release مستقل

- `offline-contracts` و `browser-synthetic-smoke`: می‌توانند PASS شوند و در محیط واقعی CI باید اجرا شوند.
- `release-acceptance`: در workflow_dispatch از مسیر گزارش، ریشه شواهد و Manifest امضاشده استفاده می‌کند. از Secret مستقل `SAKHTYAR_PHASE06_EVIDENCE_HMAC_KEY` استفاده می‌کند که هرگز در ZIP یا Log قرار نمی‌گیرد.
- بدون Secret، نبود فایل، HMAC جعلی، Timestamp نادرست، Workspace یا Principal نادرست، پاسخ تست ناموفق یا ادعای E2E ساختگی: `BLOCKED`, exit=2.
- برای تأیید واقعی، تولیدکننده شواهد مستقل CI باید اجرای HTTP مثبت/منفی، DB Principal محدود با `session_user=current_user`، Browser واقعی و اتصال REAL/TEST را از روی مکانیزم فاز ۰۲ بررسی و سپس امضا کند. **این تولیدکننده/اثبات هنوز آماده نیست و تست‌های مصنوعی امضای آزمایشی جای آن را نمی‌گیرند.**

## باقی‌مانده برای فاز اجرای Workflowهای تجاری

E2E-01 تا E2E-24 هنوز هیچ‌کدام تأیید کامل ندارند. E2E-14 Core limited، بقیه full executor ندارند. ادعای `24/24` تا تکمیل Domain Executor و شواهد معتبر ممنوع است. فاز ۰۶ فقط ابزارهای ایمن برای توسعه بعدی فراهم می‌کند.
