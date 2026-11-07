# Phase 04 - Golden Corpus و تست امنیت خزشگر

- `golden/synthetic_fa_two_pages.pdf`: دو صفحه **کاملاً مصنوعی** برای page provenance؛ قانون واقعی نیست.
- `golden/golden_pages.jsonl`: شماره صفحه واقعی + SHA PDF + متن انتظار؛ این متن صرفاً سند ساختگی است.
- `golden/expected_rules.json`: فقط محاسبات مثال، هرگز برای انتشار مقررات واقعی استفاده نشود.
- `golden/manifest.json`: SHA256 و مشخصات PDF؛ فایل‌های دیگر را برای integrity کنترل می‌کند.
- `golden/broken_zip_invalid.zip` و `golden/tampered_artifact.zip`: فایل‌های منفی و خراب؛ نباید در DB اصلی import شوند.
- `01_gate_scope_audit_readonly.sql`: فقط SELECT، برای DBA جهت ممیزی محیط.

## تست‌ها
```
cd D:\ChatGPT_Projects\SakhtYar\source\backend
mvn -pl modules/crawler -am test
mvn -pl app -am -DskipTests package
cd ..\frontend
npm run build
```

هیچ تستی با حضور شبکه بیرونی یا روی دیتابیس REAL اجرا نشود. پس از نصب، runtime startup و HTTP/DB/Browser evidence مستقل لازم است. Full E2E همچنان BLOCKED است.
