# SakhtYar Identity & Access Control v1

این بسته لایه Authentication و Authorization ساخت‌یار را کامل می‌کند.

## Authentication

- ثبت‌نام عمومی با وضعیت `PENDING`
- تأیید کاربر توسط Admin
- ورود Web با Access JWT در HttpOnly Cookie
- Refresh Token چرخشی و Persisted Session
- اتصال Access Token به Session (`sid`) برای لغو فوری نشست
- خروج و لغو نشست
- تمدید خودکار Access Token در Frontend
- ویرایش پروفایل
- تغییر رمز عبور
- مشاهده نشست‌های Web/Mobile
- لغو یک نشست یا تمام نشست‌ها
- قفل موقت حساب بعد از ۵ تلاش ناموفق
- BCrypt strength 12
- CSRF protection برای Web
- Bearer JWT برای Mobile

## Mobile API

- `POST /api/v1/auth/mobile/login`
- `POST /api/v1/auth/mobile/refresh`
- `POST /api/v1/auth/mobile/logout`

Web و Mobile از همان User/Role/Permission contract استفاده می‌کنند.

## Permissionها

- `CASE_READ`, `CASE_WRITE`
- `PROPERTY_READ`, `PROPERTY_WRITE`
- `OWNER_READ`, `OWNER_WRITE`
- `DOCUMENT_READ`, `DOCUMENT_WRITE`
- `AGENT_USE`
- `GLOSSARY_MANAGE`
- `USER_MANAGE`
- `AUDIT_READ`

## نقش‌ها

- `ADMIN`
- `PROJECT_MANAGER`
- `ANALYST`
- `LEGAL_EXPERT`
- `READ_ONLY`

## UI

- `/register` ثبت‌نام
- `/login` ورود
- `/account` پروفایل، رمز و نشست‌ها
- `/admin/users` مدیریت کاربران و دسترسی‌ها

ثبت‌نام عمومی دسترسی مستقیم نمی‌دهد. کاربر با `PENDING` و نقش `READ_ONLY` ساخته می‌شود و Admin باید او را فعال کند.

## Migration

`V7__identity_access_control.sql`

## تنظیمات

```env
APP_JWT_ACCESS_EXPIRATION_MINUTES=30
APP_REFRESH_EXPIRATION_DAYS=30
APP_COOKIE_SECURE=false
APP_COOKIE_SAME_SITE=Strict
```

در Production:

```env
APP_COOKIE_SECURE=true
```

و حتماً HTTPS و Secret تصادفی طولانی استفاده شود.

## تست

```powershell
cd backend
mvn clean test

cd ..\frontend
npm run build
```

### سناریوی دستی

1. `/register` -> کاربر ثبت شود.
2. ورود کاربر `PENDING` رد شود.
3. Admin در `/admin/users` کاربر را `ACTIVE` و نقش مناسب کند.
4. کاربر وارد شود.
5. APIها طبق Permission کنترل شوند.
6. پنج ورود اشتباه -> Lock 15 دقیقه.
7. تغییر رمز -> تمام Sessionها فوراً باطل شوند.
8. لغو Session -> Access Token مربوط به همان Session نیز فوراً نامعتبر شود.


## بازیابی رمز عبور

در این نسخه، بازیابی رمز از طریق Admin انجام می‌شود (`reset password`) و همه نشست‌های قبلی فوراً لغو می‌شوند.

Self-service «فراموشی رمز» عمداً تا زمانی که کانال تأییدشده ایمیل یا SMS به پروژه متصل نشده، فعال نشده است؛ تولید لینک بازیابی بدون کانال تحویل امن، از نظر امنیتی طراحی مناسبی نیست.
