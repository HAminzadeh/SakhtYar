# اصلاح نصب‌کننده فاز ۰۴ — Hotfix v2

## علت قطعی توقف
در GitHub شاخه v2 و HEAD `c2537a043d9f71ab92e28b3d8b69479ee3a62525` فایل `docs/PHASE04_COMMIT_EN.md` از قبل وجود داشت؛ در manifest نسخه قبلی به اشتباه `install: true` درج شده بود. کنترل عدم بازنویسی به‌درستی نصب را متوقف کرد.

## رفع مشکل
- سند Commit جدید با نام `docs/PHASE04_CLOSURE_COMMIT_EN.md` تحویل و نصب می‌شود؛ سند قدیمی هیچ تغییری نمی‌کند.
- گارد جدید، فهرست فایل‌های `install: true` را با Git index مقابله می‌کند تا فایل tracked هیچ‌گاه به‌عنوان فایل تازه نصب نشود.
- SHA-256 بسته جدید در لانچر مستقل ثابت شده است.
- محافظ‌های Git Blob، `git apply --check`، Backup، Rollback، نصب تکراری و کد اجرایی اصلی تغییر ماهوی نکرده‌اند.

## نحوه اجرا
`SakhtYar-Phase04-Closure-Hotfix-v2.zip` و `Apply-SakhtYar-Phase04-Closure-Hotfix-v2.ps1` را کنار هم در Downloads قرار دهید و اجرا کنید:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Apply-SakhtYar-Phase04-Closure-Hotfix-v2.ps1 -Mode Audit
powershell -NoProfile -ExecutionPolicy Bypass -File .\Apply-SakhtYar-Phase04-Closure-Hotfix-v2.ps1 -Mode ApplySource
```

هیچ Clone، Migration، عملیات DB، Git Commit یا Push انجام نمی‌شود. تست‌های Maven روی سیستم کاربر اجرا می‌شوند و موفقیت آن‌ها در این محیط ادعا نشده است.
