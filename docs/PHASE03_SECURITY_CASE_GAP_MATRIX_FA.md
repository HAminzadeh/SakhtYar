# فاز ۳ — گزارش اختلافات واقعی از HEAD v2

**مخزن:** `HAminzadeh/SakhtYar` — **SHA:** `178609b020eac8302824a42772fc34605942b43c` — **تاریخ:** 2026-11-04

| هدف | پیاده‌سازی موجود | تغییر این Patch | وضعیت کامل Business E2E |
|---|---|---|---|
| E2E-01 | AuthController + SessionService + JWT + lockout | قفل دیتابیس برای Refresh هم‌زمان | BLOCKED: HTTP/DB/browser نیاز دارد |
| E2E-02 | CaseAccessService + V57 ACL | API محدود grant/revoke + interceptor + حفاظت سند | BLOCKED: دو Workspace و نقش محدود نیاز دارد |
| E2E-03 | SecurityConfig دانش با USER_MANAGE | نگه‌داری Authorization Server-side، مستندات ماتریس منفی | BLOCKED: HTTP mutation و audit نیاز دارد |
| E2E-05 | Flyway V66 | ممیزی SQL خواندنی و رویه Clone-only | BLOCKED: Restore واقعی اجرا نشده |
| E2E-06 | CSRF SPA + auth cookies + mobile | الزام CSRF برای refresh وب + اصلاح کلاینت | BLOCKED: HTTP + CORS + browser نیاز دارد |
| E2E-14 | Fixture محدود روی چهار جدول | محافظت بیشتر ACL/MinIO در REAL | BLOCKED: fixture اجرایی TEST و Object Namespace غایب است |

**ریسک‌های باز:** RLS سایر دامنه‌ها، Test database principal، TEST MinIO و ۲۴ Executor کامل. این Patch عمداً موردی را PASSED نمی‌کند.

**تغییر دیتابیس:** ندارد (V67 تعریف نمی‌شود؛ V57 و V66 کافی‌اند برای تغییرات محدود این بسته). حداقل داده موجود دست‌نخورده می‌ماند. هیچ Role یا Credential خودکار تغییر نمی‌کند.
