# Hotfix راه‌اندازی محلی، Tempo و تست XML فاز ۰۴

مبنای GitHub: `a9e8eb66a4988165f8b552c8d7a54de64308f3a1`، شاخه `v2`.

## شواهد
- در اجرای IntelliJ، `db` و `redis` سالم، اما `tempo` بعد از Docker Compose به حالت `restarting` رفته است.
- `LocalInfrastructureInitializer.validateServices` قبلاً هر سرویس، حتی ابزار اختیاری مانیتورینگ، را دلیل توقف کل Backend در نظر می‌گرفت.
- کد `deploy/observability/tempo.yml` دارای بخش `compactor` سازگار با Tempo 2.x بود، درحالی‌که Tempo 3.0 این بخش را حذف کرده است. از آنجا که image برچسب `latest` دارد، نسخه Runtime باید جداگانه بررسی شود؛ بدون لاگ مستقیم Tempo نمی‌توان نسخه یا علت نهایی Crash را قطعی دانست.
- تست XML `Phase04XmlSecurityTest.acceptsValidPersianAndUtcNeutralXml` فیلدهای Property را برخلاف ترتیب `xs:sequence` در XSD نوشته بود. در XSD، `LandAreaM2` باید قبل از `ZoneCode` باشد.

## تغییرات
1. جابه‌جایی نگهداری هفت‌روزه Tempo به `overrides.defaults.compaction.block_retention` و حذف تنظیم حذف‌شده `compactor`؛ بدون لمس Volume یا داده‌های Traces.
2. `LocalInfrastructurePolicy` فقط `prometheus,grafana,tempo,loki,alloy` را اختیاری می‌شناسد؛ `db`,`redis`,`minio` و هر سرویس ناشناخته الزامی باقی می‌ماند.
3. Compose ابتدا سرویس‌های الزامی را با `--wait` بالا می‌آورد، سپس تلاش برای سرویس‌های مانیتورینگ را به‌صورت best-effort انجام می‌دهد. خطاهای اختیاری با هشدار ثبت می‌شوند.
4. تست XML فقط از نظر ترتیب معتبر اصلاح شد؛ XSD و validator تولیدی بدون تغییر باقی می‌مانند.

## محافظت‌ها
- نصب فقط فایل‌های سورس را تغییر می‌دهد، Docker و دیتابیس را اجرا نمی‌کند.
- عدم استفاده از `down`, `down -v`, `volume rm`, `prune`, `reset`, `DROP`, `TRUNCATE` یا مهاجرت جدید.
- نام Volumeهای موجود دست‌نخورده است.
- معماری PostgreSQL مشترک REAL/TEST و RLS حفظ می‌شود.
- `Audit`, `DryRun`, `ApplySource`, `Verify`, `Rollback` پشتیبان‌گیری و hash guard دارند.

## تأیید در ویندوز
1. `DryRun`, `ApplySource`, `Verify` را به ترتیب اجرا کنید.
2. در IntelliJ برنامه را با پروفایل فعلی اجرای Local دوباره Run کنید. اگر Tempo باز هم restart شد، Backend باید ادامه دهد ولی حالت هشدار Observability ثبت شود.
3. برای تشخیص دقیق Tempo، اسکریپت `scripts/diagnostics/phase04_startup_readonly_probe.ps1` را اجرا کنید؛ خروجی را پیش از ارسال از نظر اطلاعات حساس بررسی کنید.
4. `docker compose -f compose.spring-local.yml ps` باید Core را healthy نشان دهد. وضعیت Monitoring ممکن است تا زمان بررسی لاگ Tempo نیازمند اصلاح جداگانه باشد.
5. اجرای واقعی End-to-End/DB/Browser از محیط این بسته انجام نشده است.

## ریسک‌های باقی‌مانده
- بدون لاگ اصلی Tempo و نسخه دقیق cached image نمی‌توان سالم بودن خود Tempo را تصدیق کرد.
- Image با `latest` در برابر تغییرات آینده شکننده است؛ pin کردن digest پس از تأیید image هدف توصیه می‌شود، بدون pull یا حذف volume در این hotfix.
- تغییر تنظیمات Tempo 3.x می‌تواند روی خواندن بلوک‌های قدیمی اثر بگذارد؛ هیچ ارتقا، حذف یا تبدیل خودکار داده Trace در این بسته مجاز نیست.
- پایان موفق Unit Test اثبات اجرای عملی Spring Boot یا PostgreSQL نیست.
