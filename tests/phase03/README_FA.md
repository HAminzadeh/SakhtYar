# فاز ۳ — شواهد امنیت و تست‌های عملی

این بسته از HEAD `178609b020eac8302824a42772fc34605942b43c` تهیه شده است.

## تفکیک مهم

- اصلاحات کد و تست‌های قرارداد، معادل PASS تجاری E2E نیستند.
- `E2E-01/02/03/05/06/14` تا زمان اجرای HTTP واقعی، PostgreSQL با نقش محدود مستقل، مرورگر و ثبت نتیجه مثبت/منفی، **BLOCKED** هستند.
- اطلاعات/رمز عبور، Cookie، Token و PII را در Evidence ذخیره نکنید.
- برای آزمون مرورگر، پروژه فعلاً Playwright را در `frontend/package.json` ندارد؛ نصب وابستگی و سناریوهای واقعی باید جداگانه انجام شوند.

## اجرای بررسی سورس

```powershell
cd D:\ChatGPT_Projects\SakhtYar\source\backend
mvn -pl modules/identity,modules/casefile,modules/document -am test
mvn -pl app -am -DskipTests package
cd ..\frontend
npm run build
```

## گزارش خواندنی PostgreSQL

```powershell
psql -X -v ON_ERROR_STOP=1 -f tests/phase03/01_security_scope_audit_readonly.sql
```

## Gateهای شش سناریو

| سناریو | آزمون واقعی الزامی | وضعیت بدون اجرا |
|---|---|---|
| E2E-01 | login valid/invalid, refresh/replay/expire/logout + DB evidence | BLOCKED |
| E2E-02 | دو کاربر، دو پرونده، member/nonmember، grant/revoke، GET/PUT، cross-IDOR | BLOCKED |
| E2E-03 | knowledge/admin 401/403، انتشار غیرمجاز، عدم تغییر state و audit | BLOCKED |
| E2E-05 | backup/restore و intentionally failed migration روی Clone فیزیکی مستقل | BLOCKED |
| E2E-06 | CSRF absent/invalid/valid، cookie و bearer، CORS، no side effect | BLOCKED |
| E2E-14 | CRUD HTTP، دو مالک 60/40، سند TEST در فضای MinIO ایزوله، REAL invisibility | BLOCKED |

## بازیابی فقط روی Clone

۱. از دیتابیس اصلی با دسترسی مجاز، `pg_dump -Fc` بگیرید و checksum آرشیو را ذخیره کنید.
۲. دیتابیس تازه و بی‌ارتباط با REAL بسازید. پیش از Restore اتصال `SELECT current_database()` را بررسی کرده و باید نام آن صریحاً شامل `phase03_restore_clone` باشد.
۳. فقط روی Clone از `pg_restore --exit-on-error` استفاده کنید.
۴. روی Clone سلامت schema/Flyway/checksum را بررسی کنید و یک Migration عمداً ناموفق را **فقط در همان Clone** آزمایش کنید.
۵. اطلاعات ساختگی و شواهد بدون اطلاعات محرمانه ذخیره شوند؛ Clone پس از بازبینی به‌صورت دستی مدیریت شود.

**این بسته هیچ `DROP`, `TRUNCATE`, role grant, credential rotation یا Migration جدید روی دیتابیس REAL ندارد.**
