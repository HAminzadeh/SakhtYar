# شواهد، مرز اطمینان و کارهای بعدی

## مشاهده مستقیم
- repository: https://github.com/HAminzadeh/SakhtYar ؛ پوشه‌های `backend/modules/`، `frontend/src/pages/` و `docs/phase-5/` در شاخه `v2` فهرست شدند.
- `AGENTS.md` معماری React 19، Java 21، Spring Boot، توسعه روی v2 و تمایز مهندسی AI از runtime agents را بیان می‌کند.
- `docs/architecture/00-overview.md` به modular monolith و ذخیره‌سازهای PostgreSQL/PostGIS/pgvector, Redis, MinIO اشاره می‌کند.
- `docs/phase-5/phase-5.1-erd.md` روابط اصلی پرونده/ملک/مالک/سازنده/قرارداد/سند/تحلیل و lineage را نشان می‌دهد.
- `docs/phase-5/phase-5.9-financial-participation-engine.md` فرمول‌ها و مدل دو سهم هزینه/ارزش را مستند کرده است.
- `docs/knowledge/phase-5.18-review-workbench.md` گروه‌بندی منابع، تصمیم انسانی، محدودیت regex استخراج عددی و ایمنی انتشار را شرح می‌دهد.
- `docs/engineering/AI-ENGINEERING-HUB-FA.md` صفحه `/admin/engineering` و تمایز maturity را ثبت کرده است.

## سرنخ‌های گفتگو
- تغییرات review UI شامل ویرایش متن استخراج‌شده، حذف فاصله/خط اضافی، حرکت خودکار به آیتم بعدی و عملیات گروهی مطرح شده‌اند؛ اثبات وضعیت کنونی به بررسی کد و تست نیاز دارد.
- تعرفه نمونه «مالک رایگان» و «مالک پلاس» در گفتگو مطرح شده؛ فعال‌بودن آنها در محصول تأیید نشده است.
- درخواست‌های مربوط به مجموعه‌های PDF قانونی و آموزشی/JSONL، تنظیم فونت فارسی، اسکریپت PowerShell، observability و سایت وردپرسی در این پروژه مطرح شده‌اند.

## آنچه ادعا نمی‌کنیم
- ممیزی تک‌تک پیام‌های همه گفتگوهای حساب، چون همه متن‌ها در این نوبت قابل بازیابی نبودند.
- استخراج کامل تمام جداول، فیلدها، APIها و داده‌های migration؛ این کار باید با clone و ابزارهای AST/SQL روی checkout دقیق انجام شود.
- صحت اجرای تست‌ها یا آمادگی production.

## تکلیف Claude هنگام دریافت سورس
`git status`, `git branch -vv`, `git log -1`, فهرست Flyway migrations، OpenAPI واقعی، test inventory و dependency graph را استخراج کند و هر prompt را با یافته‌ها reconcile کند.
