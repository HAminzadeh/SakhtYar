# اجرای فاز ۱۷-C — نسخه سورسِ قابل‌تست

مبنا `v2@629565366c67f21d75dcff4cf4f2971fa8445335` (۲۶ نوامبر ۲۰۲۶). تغییرات در ۸ فایل اصلی/جدید + مستندات هستند و بدون Migration خودکار اعمال می‌شوند.

## اصلاحات واقعی

1. Regulation هنگام ارزیابی `propertyUpdatedAt` و `scenarioUpdatedAt` را در Snapshot ذخیره می‌کند.
2. `FeasibilityRegulationGate` از داخل Service و پس از `CaseAccess.requireWrite` کنترل می‌کند: تعلق Property/Scenario/Cost/Evaluation، REAL scope، timestamp و Snapshot نسخه‌دار، تازگی Cost، تطبیق بازار شهر/استان و زمان ارزیابی، عدم تغییر Rule و پابرجابودن مجوز Evidence از زنجیره انتشار معتبر. پاسخ 409 برای Snapshot قدیمی یا ناقص.
3. ارزیابی قدیمی که فیلدهای نسخه را ندارد باید دوباره انجام شود؛ داده تاریخی حذف یا دستکاری نمی‌شود.
4. وضعیت COMPLIANT بدون `legalCoverageCertified=true` و Ruleهای معتبر قابل مصرف برای تصمیم قطعی نیست. وضعیت REVIEW_REQUIRED فقط تحلیل مشروط تولید می‌کند.
5. UI صفحه پرونده وضعیت‌ها و خروجی جداگانه Ruleها را با جزئیات نشان می‌دهد؛ مسیر فایل خصوصی هم در خروجی Backend و هم در UI پنهان می‌شود. تاریخ شمسی/RTL و cache key وابسته به case/property/updatedAt هستند.
6. Workbench موجود Knowledge دارای جدول PREVIEW خواندنی از Bridge می‌شود؛ هیچ ابزار تأیید/انتشار خودکار اضافه نشده است.

## نواقص مهم که عمداً مخفی نشده‌اند

- تحویل نهایی کامل ۷۵/۷۵ نیست: طبق ماتریس A/B موارد BLOCKED همچنان باز هستند.
- غیرفعال‌سازی E2E REAL تا تأیید واقعی حقوقی و پوشش تمام تبصره‌ها ضروری است.
- Server-side freshness برای منبع و Snapshot نسبت به اطلاعات موجود قوی‌تر شده، اما تضمین consistency میان تراکنش‌ها و تغییر هم‌زمان ملک بدون قفل/Version Ledger در DB نیاز به تست Clone دارد.
- Feature flag تصمیم‌ساز `app.regulation.decision.enabled` پیش‌فرض false است، اما Shadow telemetry و استقرار مرحله‌ای نیازمند طراحی/آزمایش بعدی‌اند؛ اقدام ۷۵ BLOCKED می‌ماند.
- Endpoint خصوصی دانلود PDF با کنترل ACL و صفحه/Hash مستقل موجود بودنش تأیید نشده؛ لینک ساختگی ایجاد نشده است.

## اجرای بسته

فقط ZIP و نصب‌کننده PowerShell را در Downloads بگذارید؛ `SelfTest -> Audit -> DryRun -> ApplySource -> Verify`. هیچ Migration یا DB UPDATE اجرا نمی‌شود. برای Rollback از مسیر Backup اعلام‌شده پس از ApplySource استفاده کنید.

## آزمایش‌های واقعی

Java 21 مستقل روی Gate باید compile شود؛ تست‌های JUnit Maven و TypeScript/Vite فقط پس از اجرای PowerShell روی سورس واقعی VERIFIED خواهند شد. گزارش ارسالی فاز B: ۱۳/۱۳ ماژول و ۲۰۷/۲۰۷ تست موفق؛ این آمار مربوط به C نیست.
