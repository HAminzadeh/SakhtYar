# فاز ۰۴ — رفع آسیب‌پذیری‌های Frontend

مبنای محتوایی: Blobهای Frontend یکسان با HEAD اصلی؛ تأیید مجدد روی `v2/8d82a02192a5a96a9e4e69de994a68e25c7d332a`. در این نسخه تغییر HEAD نامرتبط مجاز است ولی تغییر Blob فایل‌های هدف رد می‌شود.

## تغییرات

- ارتقای `@neshan-maps-platform/maplibre-sdk` از `5.24.4` به `6.16.0` جهت خروج از MapLibre GL 5 و آسیب‌پذیری XSS.
- ارتقای `vite` به `8.3.6`.
- ارتقای `zod` به `4.6.7`.
- ثبت `npm overrides` جهت تثبیت `source-map-js@1.2.2` (وابستگی غیرمستقیم).
- تولید واقعی `package-lock.json` از طریق `npm install` روی کامپیوتر توسعه‌دهنده، نه ساخت Lockfile فرضی.
- آزمون نسخه‌ها و ساختار نقشه؛ `npm audit --audit-level=low`؛ و `npm run build`.

## پیش‌نیازها

- دسترسی به npm Registry برای دانلود نسخه‌های اصلاح‌شده؛ دسترسی اینترنت برای این نصب الزامی است.
- Node.js >=24 (مطابق engines پروژه) و npm.
- امکان ذخیره در `D:\ChatGPT_Projects\SakhtYar\source`؛ شاخه `v2`.
- نصب با وجود تغییرات **دیگر** فاز ۰۴ در Working Tree مجاز است، اما تغییراتِ قبلی `frontend/package.json` یا `frontend/package-lock.json` باید محافظت شوند.

## نکته SDK نشان

نسخه ۶ SDK از MapLibre GL v6 با نیاز به **WebGL2** و `DecompressionStream` استفاده می‌کند. مسیرهای import و style.css مورد استفادهٔ فعلی ثابت مانده‌اند و کد PropertyMap از قبل ساخت Map را در `try/catch` انجام می‌دهد؛ اما نمایش نقشه و مارکر، ظاهر برچسب‌های فارسی، Drag و Save باید در مرورگر با کلید واقعی نشان دستی تست شوند. دستگاه‌های فاقد WebGL2 به نقشه دسترسی نخواهند داشت.

## امنیت و مرزها

این لانچر فقط `frontend/package.json`، `frontend/package-lock.json`، و فایل آزمون/مستندات را تغییر می‌دهد؛ بدون Clone، SQL، Migration، حذف Volume، و تغییر داده‌های REAL/TEST.

اگر Audit صفر نباشد، یا Build شکست بخورد، گزارش وضعیت **BLOCKED** است؛ حتی اگر بسته‌ها دانلود شده باشند. Launcher به‌صورت خودکار Commit/Push انجام نمی‌دهد. قبل از Push، `package-lock.json` را نیز Commit کنید.

## اجرای دوباره

اسکریپت idempotent است و اگر نسخه‌های موردنظر قبلاً ثبت شده باشند، فایل‌های اصلی را دوباره بازنویسی نمی‌کند؛ فقط `npm install`، ممیزی و Build را دوباره ارزیابی می‌کند. در حالت خطا، نسخه‌های پشتیبان با timestamp در پوشه `SakhtYar-PatchBackups/FrontendSecurity-*` نگهداری می‌شوند.
