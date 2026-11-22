# Phase 3 — تحویل و محدودیت‌ها

مبنای بررسی: GitHub v2 / cc77cd56264ca28e77ba7dc35d733e1aac2ab841.

## آنچه واقعاً پیاده‌سازی شده
- چهار Spring Component برای E2E-21..24 با Registry موجود و نوع OFFLINE_CONTRACT_ONLY.
- آزمون‌های آفلاین AI/Tool ACL، URL مقصد مرورگر، Dedup و Cooldown، Draft/Review/Rollback.
- تست‌های مستقیم روی Fake Outbox و Config Sandbox موجود از فاز ۰۶ (عدم ساخت سیستم موازی).
- Playwright synthetic RTL و Viewport روی HTML مصنوعی؛ بدون login یا سرور برنامه.
- گزارش read-only مربوط به ۲۴ سناریو؛ تمایز Source Presence از Runtime Proof.
- CI آفلاین، تست Release Gate و Proof Verifier فعلی.

## مواردی که **تکمیل نشده‌اند**
- سناریوهای E2E-01..06 و E2E-14 هنوز Executor تجاری کامل ندارند؛ ۱۳ Executor فاز دوم فقط Offline Contract هستند.
- E2E-21 اجرای HTTP واقعی Agent و امنیت Prompt/Tool در اجرای واقعی ندارد.
- E2E-22 آزمون Login واقعی، RTL واقعی اپلیکیشن، 403/404 و Browser Evidence ندارد.
- E2E-23 اتصال HTTP/DB به Outbox TEST همراه با تأیید Physical Restricted DB Role ندارد.
- E2E-24 Config Sandbox به API واقعی TEST وصل نشده؛ تغییر Config واقعی ممنوع است.
- شواهد حقیقی ۹۶ فایل و CI HMAC Attestation فراهم نیست. شمار پذیرش واقعی صفر است مگر با گزارش مستقل مجاز اثبات شود.
- E2E-05 به دلیل ممنوعیت DB Clone/Restore برای بخش بازیابی BLOCKED باقی می‌ماند.
- **۴ سناریوی پایانی هنوز Business E2E کامل نیستند**؛ هیچ تظاهر به تکمیل وجود ندارد.

## روش انجام تست‌ها
1. Java smoke و Node report test آفلاین.
2. Maven reactor با `mvn -pl modules/quality -am test` بدون شروع Backend.
3. Frontend build جداگانه؛ اجرای مرورگر مصنوعی جداگانه.
4. Live Business E2E فقط پس از اثبات Gate فاز اول و Test Isolation. به هیچ عنوان با نصب‌کننده آغاز نمی‌شود.

## Release
`BLOCKED` تا بررسی مستقل TEST scope، نقش فیزیکی محدود دیتابیس، API positive/negative، شواهد Browser، Artifact SHA256 و HMAC CI. اجرای موفق Build صرفاً سلامت کد را می‌رساند.
