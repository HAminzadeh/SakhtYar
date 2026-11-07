# تحویل فاز ۶ و پیش‌شرط فاز بعد — ۲۰۲۶/۱۱/۰۷

**HEAD GitHub `v2`:** `ad862c46e097273ae8b5d9a49897b630bfe343f6`، در زمان مشاهده `2026-11-07T07:06:53Z`.

**آخرین پنج Commit:** `ad862c46`, `578024f0`, `2a1bc507`, `178609b0`, `6a82eb11`.

## فایل‌های اصلاح‌شده (Patch anchor + Git blob)
- `backend/modules/intelligence/src/main/java/com/sakhtyar/intelligence/application/WorkflowExecutor.java` — baseline blob `434f78b111ba8cb0d3f8c14894286d2469f10bd2`
- `backend/modules/intelligence/src/main/java/com/sakhtyar/intelligence/application/WorkflowDagValidator.java` — baseline blob `ddf052f00080633f245100be3b57d85419fdf94e`
- `backend/modules/agents/src/main/java/com/sakhtyar/agents/core/AgentBus.java` — baseline blob `532a7a099fc0b3e8564cdec663fb05b3029b7b3f`
- `frontend/src/pages/SimulationCenterPage.tsx` — baseline blob `179583072b7cd17ca375bc90a0faa78ce4faebcf`
- `backend/modules/intelligence/pom.xml` — baseline blob `0f60d70ec443be6ce7971336592a5a5a189ac070`

## فایل‌های افزوده
- `.github/workflows/phase06-quality-gate.yml`
- `backend/modules/intelligence/src/test/java/com/sakhtyar/intelligence/application/Phase06WorkflowDagBoundaryTest.java`
- `docs/PHASE06_AI_UI_RELEASE_GAP_MATRIX_FA.md`
- `docs/PHASE06_COMMIT_EN.md`
- `tests/phase06/README_FA.md`
- `tests/phase06/browser/login-smoke.spec.mjs`
- `tests/phase06/browser/package.json`
- `tests/phase06/browser/playwright.config.mjs`
- `tests/phase06/release_gate.contract.test.mjs`
- `tests/phase06/release_gate.mjs`

## وضعیت و نتایج واقعی
- CI Live Backend/Frontend: هنوز اجرا نشده؛ user Windows مورد نیاز.
- Java 21 DAG bounded smoke: روی محیط مستقل ۶ مورد PASS؛ بدون Spring runtime.
- Offline Release Gate: تست قرارداد Node.js PASS، گزارش واقعی کاربر `24 BLOCKED`, خروجی strict Exit=2.
- Script Installer: ۴ حالت روی Git مصنوعی PASS، idempotence PASS؛ Windows واقعی هنوز اجرا نشده.
- E2E-21 تا ۲۴: BLOCKED؛ Executor واقعی، Fake agent, browser flow, synthetic monitoring, config restore انجام نشده.
- ۲۴/۲۴ نهایی: BLOCKED؛ no real full business execution evidence.
- Flyway: هیچ Migration جدید؛ آخرین موجود V66. بدون SQL installer، بدون دست‌کاری REAL.

## آزمایش‌های لازم پس از نصب
```powershell
cd D:\ChatGPT_Projects\SakhtYar\source\backend
mvn -pl modules/intelligence -am test
mvn -pl app -am -DskipTests package
cd ..\frontend
npm ci
npm run build
cd ..
node tests/phase06/release_gate.contract.test.mjs
```

## پیش‌شرط‌های آزادسازی و فاز بعد
1. اول E2E-01/05 و Restricted DB principal/REAL-TEST isolation را واقعاً کامل و PASS کنید.
2. Agent fake provider، tool allowlist و تست prompt injection با no-outbound evidence ساخته شود.
3. مرورگر Playwright با TEST login و چرخه case/property/document در شبکه ایزوله اجرا شود؛ tokens/cookies وارد artifacts نشوند.
4. Monitoring fake outbox، retry/dedup، صفر ارسال واقعی و config version restore در clone تست شوند.
5. source_commit واقعی از build تزریق شود؛ post-run release validation با hash artifact، DB principal و مرورگر مستقل انجام شود.
6. هر سناریوی فاقد Evidence باید BLOCKED بماند؛ گیت strict باید Exit غیرصفر بدهد.

**این بسته Release واقعی نیست؛ مرحله امنیت/گزارش‌گیری آماده نصب است.**
