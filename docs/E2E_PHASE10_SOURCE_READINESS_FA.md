# ادامه چهار فاز E2E — بسته Phase10 (منطبق با v2)

این بسته یک گام **توسعه سورس** است، نه ادعای پایان ۲۴ Business E2E.

## فاز ۱ — پایداری و Build
- کنترل Phase09 قبلی و JSX جدید حفظ می‌شود.
- برای خطای قبلی IntelliJ، ابتدا Maven Reload و بازسازی `backend/app` و `modules/material` لازم است.
- در IntelliJ حتماً `SakhtYar Backend - Development` را انتخاب کنید. `local` مجوز E2E واقعی نیست.
- خروجی قطعی `npm run build` و `mvn -pl app -am test` باید مستقلاً اخذ شود.

## فاز ۲ — ایزولاسیون
- قراردادهای fail-closed پیش از هر اولین Business Write در CI بررسی می‌شوند.
- نتیجه بدون LOGIN محدود مستقل، کنترل مثبت، RLS تمام دامنه، محصورسازی APIها، Cleanup و Evidence Store همچنان `BLOCKED` است.
- این بسته هیچ دیتابیس، Role، RLS، Migration، Seed یا Docker Volume را تغییر نمی‌دهد.

## فاز ۳ — پوشش ۲۴ سناریو
- کاتالوگ از هر پوشه اجرایی قابل اعتبارسنجی است.
- ۲۴ ID و زنجیره وابستگی و چهار preflight بررسی می‌شود.
- شمارش `FULL_BUSINESS_E2E` پویا است، اما metadata اثبات PASS نمی‌شود.
- اجرای ۲۳ گردش‌کار ناقص هنوز باید با Fixture، HTTP مثبت/منفی، DB assertion، Browser assertion، Evidence و Cleanup توسعه یابد.

## فاز ۴ — Regression و CI
- Workflow جدید فقط قراردادهای Source را اجرا و JSON قابل بارگیری منتشر می‌کند.
- گزارش Source PASS هرگز معادل پذیرش Business E2E نیست.

## دستورهای اختیاری و بدون نوشتن دیتابیس

```powershell
cd D:\ChatGPT_Projects\SakhtYar\source
node scripts/e2e_final_readiness.mjs --report .final-e2e-reports\source-readiness.json
node tests/e2e/check_24_plans.mjs
```

تست HTTP صرفاً GET بوده و به مدیر احرازشده احتیاج دارد:

```powershell
# در ترمینال شخصی خود Credential را به صورت ENV فراهم کنید. در Git قرار ندهید.
node tests/e2e/read_only_runtime_smoke.mjs
```

## گیت پذیرش
- Source CONTRACT PASS: کنترل ساختاری موفق.
- Build PASS: مستقل و نیازمند نصب ابزارهای محیط.
- Runtime Isolation PASS: تنها با شواهد واقعی مستقل.
- Business E2E PASS: فقط با اجرای واقعی، شواهد معتبر و Assertionهای مثبت و منفی.

هر `BLOCKED` یا `NOT_VERIFIED` باید به همان شکل گزارش شود.
