# Handoff فاز ۰۶ — توسعه فرآیندهای تجاری

1. Fake adapters را فقط به‌صورت Explicit Constructor در Unit/Component Tests بسازید. هیچ‌کدام @Service/@Component ندارند و به REAL Registry راه پیدا نمی‌کنند.
2. برای Workspace/Run از شناسه‌های مصنوعی و `Phase02FixtureKeys` استفاده کنید؛ `Phase02FixtureFactories` برای Workflow نامعتبر باید BLOCKED بدهد.
3. در اجرای Browser، فقط درخواست‌های Loopback با Mock پاسخ بگیرند و داده محرمانه/Token/Cookie در Screenshot، Trace یا HTML ذخیره نشود.
4. هر Business Executor مجزا پس از اثبات Principal محدود، HTTP مثبت و منفی، Browser و Signed Evidence اضافه شود. TESTER بودن در JWT برای DB کافی نیست.
5. Release Gate بدون HMAC CI مستقل و ۹۶ Artifact معتبر Fail Closed می‌ماند. ساخت Manifest با اسکریپت محلی Developer مجوز Release نیست.
6. هیچ عملیات Clone یا دیتابیس توسط این فاز/لانچر انجام نمی‌شود. Gateهای نیازمند تأیید DATABASE تا ارائه شواهد خارجی قابل قبول BLOCKED هستند.
7. هر Build در Windows باید با Report و SHA موجود در HEAD و اجرای تست واقعی تطبیق داده شود؛ نام کلاس به‌تنهایی PASS نیست.
