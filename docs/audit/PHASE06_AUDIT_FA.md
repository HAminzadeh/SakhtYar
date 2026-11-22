# فاز ۰۶ — ممیزی و ثبت رویدادهای قابل اتکا (ساخت‌یار)

- GitHub baseline: `ff3e3c6123a6a3f41f0511ffaac64439efabf672` (branch `v2`)
- Module: `backend/modules/audit`
- Migration: NONE
- Status: READY_TO_VERIFY — no claim of runtime immutability, RLS or REAL/TEST proof

## شواهد واقعی HEAD و تصمیم‌های فنی

| مسیر / رفتار | Before | After | وضعیت اثبات |
|---|---|---|---|
| `AuditService.record`, `recordAs` | `REQUIRES_NEW` سبب ثبت Audit مستقل از rollback عملیات اصلی | `REQUIRED` برای عضویت در تراکنش موجود | بررسی سورس/تست JUnit، نه DB|
| `AuditService.toJson` | هر `Map` مستقیماً serialize می‌شود؛ serialization failure به JSON ظاهراً موفق تبدیل می‌شد | پالایش Allowlist، خطای fail-closed | Source/JUnit |
| `AuditPayloadSanitizer` | وجود ندارد | فقط enum-like labels، اعداد و boolean محدود؛ ناشناخته/PII حذف | Source/JUnit |
| `traceId` | در مدل Audit نیست | traceId فقط از MDC با قالب محدود در JSON payload ذخیره می‌شود | Source/JUnit؛ propagator E2E اثبات نشده |
| `workspaceId` | هیچ trusted context برای کاربر/تستر دیده نشد | عمدی: از payload و header دریافت نمی‌شود؛ باید گیت جداسازی دیتابیس پیاده شود | BLOCKED |
| `AuditEventEntity` | بدون محافظ JPA برای update/delete | `@PreUpdate/@PreRemove` برای جلوگیری از تغییر تصادفی JPA | فقط defense-in-depth؛ DB immutable **نیست** |
| `AuditEventRepository` | `JpaRepository` ساده | queryهای محدود، مرتب‌شده و صفحه‌بندی‌شده | Unit/Source |
| `AuditReadController` | وجود ندارد | GET admin audit read، max 50، بدون payload خام، بررسی مضاعف دسترسی | Unit/Source؛ HTTP واقعی انجام نشده |
| `SecurityConfig` | audit route اختصاصی ندارد | مجوز صریح `AUDIT_READ` برای GET؛ باقی audit routeها denyAll | Source/Unit |
| `audit_event` در V2 | ستون‌های id/type/id/action/actor/payload/created_at + indexها | بدون تغییر | DATABASE UNTOUCHED |

## نمودار وابستگی

```text
backend/app (Spring Boot scan + security config)
  -> modules/identity (UserRole, AUDIT_READ, SecurityConfig)
  -> modules/audit (AuditService, AuditPayloadSanitizer, AuditReadController)
       -> Spring Data JPA -> existing public.audit_event (V2)
  -> business modules (Identity, Casefile, Document, ... -> AuditService)
  -> Quality (اجرای شواهد مستقل، نه جایگزینی منطق Audit)
```

## Threat model و مرزبندی

1. **Data exfiltration:** payload ناشناخته، username/email/password/token، متن آزاد و nested payload ذخیره نمی‌شود. actor صرفاً برای مدیران دارای `AUDIT_READ` قابل خواندن است. رکوردهای قدیمی به‌صورت خودکار پاکسازی نمی‌شوند؛ خروجی API payload خام را نشان نمی‌دهد.
2. **Audit false success:** در صورتی که عملیات دارای تراکنش اصلی باشد، موفقیت Audit بخشی از همان تراکنش است؛ در صورت failure تراکنش fail-closed است. در صورت نبود تراکنش Caller، `REQUIRED` تراکنش جدید می‌سازد. مرز Outbox مستقل برای rejected-event و جزئیات failure E2E هنوز ساخته نشده است.
3. **Replay/idempotency:** رویدادهای با action/aggregate برابر دارای UUID مستقل هستند؛ deduplication جهانی ارائه یا ادعا نمی‌شود. نیازمند idempotency key مورد اعتماد و unique constraint آزمایش‌شده است.
4. **Integrity:** محافظ `@PreUpdate/@PreRemove` فقط برای JPA است. PostgreSQL direct UPDATE/DELETE، superuser و retention هنوز سخت‌گیری DB مستقل ندارند؛ append-only DB باید در Migration آینده با clone، RBAC/RLS و پشتیبان آزمایش شود.
5. **Workspace:** هویت App TESTER معادل DB role نیست. `workspaceId` از Header/Map ورودی قابل اعتماد نیست؛ هنوز اثبات جداسازی `REAL/TEST` انجام نشده است. اجرای تست نوشتنی تا اثبات RLS ممنوع.
6. **Read endpoint:** فقط `GET /api/v1/admin/audit/events`، `AUDIT_READ`، ممنوع برای `TESTER` حتی با grant اشتباه، حداکثر ۵۰ نتیجه در صفحه، فیلتر جفت `aggregateType/aggregateId`. خروجی شامل actor (شناسه حساب) و بدون `payload_text`، JWT یا password است.
7. **Correlation:** format مجاز MDC = 16 یا 32 نویسه hex. بدون MDC معتبر traceId ثبت نمی‌شود و این به معنی فقدان رویداد نیست. جست‌وجوی traceId در دیتابیس فعلاً index ندارد؛ lookup کارآمد نیازمند Migration و تست DB است.
8. **Retention/export:** auto-delete/export فعال نشده تا بدون خط‌مشی رسمی و backup منجر به آسیب/نشت داده نشود.

## نقشه قابلیت‌ها و Acceptance Gate

- Real source patch and offline contracts: locally runnable, `READY_TO_VERIFY`.
- Java 21 JUnit: در target ویندوز Maven آفلاین با `-pl modules/identity -am test` اجرا شود.
- HTTP 401/403 and pagination + frontend: NO LIVE PROOF، پیاده‌سازی API جدید UI را به‌صورت خودکار تغییر نمی‌دهد.
- Concurrent same-event / rollback / DB constraints: BLOCKED without isolated verified TEST workspace.
- RLS / DB-level append-only and checksum chain: BLOCKED; no Migration was written.
- No schema changes, no Docker actions, no background jobs, no synthetic PASS for runtime.

## اجرای ایمن روی ویندوز

```
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Downloads\Apply-SakhtYar-Audit-Phase06.ps1" -Mode Audit
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Downloads\Apply-SakhtYar-Audit-Phase06.ps1" -Mode DryRun
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Downloads\Apply-SakhtYar-Audit-Phase06.ps1" -Mode ApplySource
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Downloads\Apply-SakhtYar-Audit-Phase06.ps1" -Mode Verify
```

قبل از Push: `git status --short`, `git diff --check`, Maven `BUILD SUCCESS` و نتیجه startup واقعی.
