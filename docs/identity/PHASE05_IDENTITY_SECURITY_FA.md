# فاز ۰۵ — هویت، نشست و کنترل دسترسی

**Baseline GitHub:** `f09e93b145476b13b5160dc6bdf0c07c76748d3a` (2026-11-22T06:23:17Z)
**وضعیت:** `READY_TO_VERIFY`، نه پذیرش Production
**معماری:** یک PostgreSQL مشترک با `data_scope` و `test_workspace_id`؛ هیچ DB clone جدیدی برای اجرای کسب‌وکار ایجاد نمی‌شود.

## ممیزی سورس و شکاف‌ها

| Path / Contract | شاهد HEAD | نقص | اولویت | اقدام | آزمون |
|---|---|---|---|---|---|
| `identity/application/AuthService.authenticate` | `@Transactional(noRollbackFor=ResponseStatusException.class)`، شمارنده ناموفق | خواندن کاربر بدون lock و خطر lost update همزمان | P0 | خواندن با `PESSIMISTIC_WRITE`، حفظ عدم rollback شمارنده | `Phase05IdentityConcurrencyTest`؛ تست DB واقعی مانده |
| `identity/application/UserAdminService.update` | `countByRoleAndStatus` بدون lock | race کاهش آخرین دو مدیر | P0 | lock همه مدیران فعال با ترتیب شناسه، سپس شمارش همان رکوردهای قفل‌شده | تست واحد؛ DB concurrency مانده |
| `identity/application/AuthService.register` / `UserAdminService.create` | precheck وجود username/email و سپس `save` | race ایجاد دو کاربر هم‌نام ممکن است به خطای ۵۰۰ دیرهنگام برسد | P1 | `saveAndFlush` و پاسخ conflict کنترل‌شده قبل از audit | `Phase05RegistrationRaceTest`؛ تست واقعی race مانده |
| `identity/application/UserAdminService.update` | `revokeAll` فقط در غیرفعال‌سازی | نقش جدید بدون re-auth روی نشست قبلی | P1 | revoke هنگام هر تغییر Role | Unit test |
| `identity/application/AuthSessionService.revokeAll/revokeSession` | lookup ساده | رقابت refresh/revoke | P1 | lock همسان با `rotate` | Unit+reflection؛ DB concurrency مانده |
| `identity/domain/AuthSessionRepository` | lookup Refresh با `PESSIMISTIC_WRITE` | پیاده‌سازی صحیح قبلی | موجود | Reuse | `Phase03RefreshLockContractTest` |
| `identity/domain/UserRole.TESTER` | فقط QUALITY_VIEW و QUALITY_RUN_SAFE | کد نقش محدود است، ولی SQL RLS جدا اثبات می‌شود | موجود | حفظ | `UserRolePermissionTest` |
| `identity/application/AdminBootstrap` | reset هنگام شروع false | نبود تأیید تست همگانی | P2 | افزودن تست عدم ارتقای خودکار کاربر قبلی | `Phase05AdminBootstrapSafetyTest` |
| `frontend/pages/LoginPage/RegisterPage/UsersPage/AccountPage` | Callerهای API موجود | این patch تغییر JSON/API ندارد | موجود | بدون تغییر UI | `npm run build` روی ویندوز در صورت نیاز |
| `SecurityConfig` | CSRF SPA, CORS exact, stateless | تست HTTP جامع همه مسیرها هنوز کافی نیست | باز | بدون تغییر ناامن | HTTP runtime pending |

## معماری و گراف وابستگی

`Frontend Login/Register/Users/Account` → `AuthController/UserAdminController` → `AuthService/UserAdminService/AuthSessionService` → `UserRepository/AuthSessionRepository` → **همان PostgreSQL**.
`SecurityFilterChain` → `JwtCookieAuthenticationFilter` → `JwtService` و `DatabaseUserDetailsService`؛ نشست و نقش از دیتابیس بررسی می‌شود.
`AuditService` یک وابستگی جدا برای ثبت رویدادهای غیرحساس است. در ثبت هم‌زمان کاربر/TESTER، تصادم unique با 409 کنترل‌شده و بدون ثبت Audit موفقیت مواجه می‌شود. `Quality` فقط assertions و evidence را نگه می‌دارد و منطق هویت را پیاده‌سازی نمی‌کند.

## تهدیدها و سیاست‌ها

- **Brute force:** lookup کاربر با lock تراکنش؛ شمارنده ۵ تلاش و قفل ۱۵ دقیقه‌ای قبلی حفظ می‌شود.
- **Lost-admin race:** تمام مدیران ACTIVE با lock به ترتیب UUID خوانده می‌شوند و همان لیست مبنای محافظ آخرین مدیر است؛ هیچ دستور تغییر نقش مخفیانه اجرا نمی‌شود.
- **Session theft/replay:** Refresh Token تصادفی ۴۸ بایت، نگهداری SHA-256، rotation با lock؛ هر session-revoke و revoke-all نیز به lock محافظ مجهز می‌شود. لغو سمت دیتابیس باعث بی‌اعتبار شدن Access JWT هنگام درخواست بعدی می‌شود.
- **Privilege elevation:** تغییر نقش، sessionهای قبلی را باطل می‌کند؛ نقش TESTER صرفاً Quality امن است، نه role فیزیکی دیتابیس.
- **CSRF:** کوکی‌های HttpOnly برای Access/Refresh؛ `XSRF-TOKEN` و header `X-XSRF-TOKEN` برای عملیات غیرمعاف؛ روی موبایل Bearer جداگانه است.
- **Password/bootstrap:** reset خودکار مدیر پیش‌فرض false؛ secrets را فقط به‌صورت ENV محدود نگه دارید، نه Git یا گزارش تست. هیچ پیکربندی جدیدی از رمزهای ثابت به سورس افزوده نشده است.
- **IDOR:** ابطال نشست غیرمتعلق به خود کاربر باید 404 بدون نوشتن بدهد.

## سازگاری و محدودیت‌ها

- مسیرها، DTOها، JSON، نقش‌ها و Migrationها بدون تغییر باقی می‌مانند.
- عملکرد DB در زیر بار: `SELECT FOR UPDATE` ردیفی می‌تواند انتظار قفل ایجاد کند؛ در Production میزان latency و timeout باید اندازه‌گیری شود.
- خواندن lock با transaction واقعی ضروری است و با Mockito یا contract-test صرفاً اثبات نمی‌شود.
- «با موفقیت بالا آمدن برنامه» معادل اثبات جداسازی سراسری REAL/TEST نیست؛ تست منفی workspaceها، HTTP E2E با داده TEST و permission matrix باید اجرا شوند.
- اجرای دوباره تست Python بدون نوشتن فایل‌های موقت (`python -B`) است؛ Patch نصب را بدون عملیات شبکه، Docker، Migration یا دیتابیس انجام می‌دهد.

## Gate مورد نیاز

1. `DryRun` و `ApplySource` با هش و Git anchor صحیح.
2. `Verify`: تست‌های Python و Maven (Java 21) تمام ماژول identity و وابستگی‌های Maven؛ صفر FAIL.
3. تست PostgreSQL واقعی با دو login هم‌زمان برای شمارنده قفل؛ و دو admin موجود در workspace تست مجاز برای race کاهش مدیران، **فقط پس از اثبات RLS/اختیار و تهیه snapshot ایمن**.
4. تست HTTP ورود، خروج، انقضا، revoke، CSRF، 401/403، session IDOR، UI و مجوزها (با fixture غیرواقعی مجاز).
5. بدون تأیید مراحل ۳ و ۴ وضعیت Production کامل اعلام نشود.

## Rollback

نصب‌کننده Backup خارج از سورس می‌گیرد. `Rollback -BackupDir <path>` تنها در صورت تطابق تمام فایل‌ها با SHA ثبت‌شده عمل می‌کند؛ هیچ SQL rollback، git reset یا volume delete انجام نمی‌دهد.
