# Phase 06 — Runbook (FA) / رهاسازی با شواهد قابل دفاع

Baseline GitHub v2: `ad862c46e097273ae8b5d9a49897b630bfe343f6` (2026-11-07).

**این بسته فقط بخشی از فاز ۶ را پیاده‌سازی می‌کند؛ هیچ E2E جدیدی PASSED نشده است.**

## نصب
فایل ZIP و لانچر PowerShell را بدون بازکردن دستی در Downloads قرار دهید.
لانچر چهار حالت Audit, DryRun, ApplySource, Verify دارد. پیش از تغییر، Git HEAD/branch و Blobهای هدف را می‌سنجد، به موارد Stageشده یا تغییرکرده دست نمی‌زند، Backup می‌گیرد و روی REAL هیچ عملیات دیتابیسی انجام نمی‌دهد.

## تست محلی
- `cd backend; mvn -pl modules/intelligence -am test`
- `cd backend; mvn -pl app -am -DskipTests package`
- `cd frontend; npm ci; npm run build`
- `node tests/phase06/release_gate.contract.test.mjs`
- `node tests/phase06/release_gate.mjs business-e2e-report.json` -> در صورت BLOCKED خروجی ۲
- `cd tests/phase06/browser; npm install --ignore-scripts; npx playwright install chromium; npm test`

**Playwright فقط Login shell را در localhost باز می‌کند؛ این E2E-22 نیست.** پیکربندی trace/video/screenshot را غیرفعال کرده تا توکن و کوکی در artifactهای عمومی باقی نماند. برای تست‌های تجاری آینده فقط با TEST namespace و credential جداگانه از CI secrets استفاده شود.

## گیت آزادسازی
برای ۲۴/۲۴ واقعی، علاوه بر چک ساختاری باید HTTP مثبت/منفی، DB تحت principal محدود، شاهد مرورگر، آزمایش restart/replay، ایزوله بودن outbound و audit یکپارچگی به‌طور مستقل کنترل شود. `--inventory` صرفاً فهرست است و هیچ تأیید Release محسوب نمی‌شود.

## شکاف‌های باقی‌مانده
E2E-21: fake agent provider، تست prompt injection و DAG کامل. E2E-22: login/case/property/document واقعی. E2E-23: synthetic monitoring events، fake sender/outbox و retries. E2E-24: sandboxed config activation and rollback. تمام ۲۴ سناریو در آخرین JSON کاربر BLOCKED بودند. `source_commit=unknown` بود. برای حل آن در اجرای Spring Boot مقدار `sakhtyar.source-commit` باید از Git HEAD به‌صورت امن تزریق شود، نه متن ثابت.

هیچ Migration تازه‌ای نیست؛ V66 آخرین Migration است. نصب‌کننده Restore/Database write انجام نمی‌دهد.
