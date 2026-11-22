# ورود دانش از هوش مصنوعی — V53.0

## هدف

مسیر مستقلی کنار «مرکز دانش» برای تولید دستور استاندارد، دریافت بسته ZIP/RAR و ورود **نامزدهای دانش** به جداول مرحله‌بندی.

## بسته استاندارد

فقط ۴ فایل UTF-8 در ریشه آرشیو:

- `manifest.json` — دارای `schema_version=sakhtyar-ai-knowledge-v1`, `instruction_version`, `source_ai`, `review_policy=HUMAN_REQUIRED`.
- `documents.jsonl` — در هر خط شیء JSON با `doc_key`, `title`, `category`, `source_reference`, `authority` (اختیاری), `source_sha256` (اختیاری).
- `rules.jsonl` — احکام پیشنهادی صریح؛ در هر خط `doc_key`, `item_key`, `segment_type`, `article_label`, `full_text`, `evidence_quote`, `page_from`, `page_to`.
- `legal_segments.jsonl` — مواد، تبصره‌ها و بندها با همان فیلدها؛ `PASSAGE` برای متن مبهم/غیرقانونی.

`category`: `LAW`, `EDUCATION`, `TECHNICAL`, `REFERENCE`, `OTHER`, `UNCLASSIFIED`.
`segment_type`: `ARTICLE`, `NOTE`, `CLAUSE`, `PASSAGE`.

**سقف هر بسته:** ۲۴ مگابایت فشرده، ۳۰۰۰ رکورد، سقف محدودیت داخلی برای داده بازشده.

## امنیت و صحت

- فایل ZIP مستقیماً با `ZipInputStream` خوانده می‌شود؛ به مسیر فایل سیستم استخراج نمی‌شود.
- برای RAR در صورت نصب 7-Zip، فقط ۴ نام فایل مورد انتظار با `7z e -so` به حافظه خوانده می‌شوند. هیچ مسیر داخلی آرشیو به‌صورت مستقیم استخراج نمی‌شود.
- ادعای `evidence_quote` فقط از نظر **وجود عینی در `full_text` ادعایی AI** بررسی می‌شود، نه صحت در PDF اصلی.
- نام، کد، مسیر و اندازه محدود می‌شوند؛ فایل‌های تکراری و ناشناس ZIP مردود هستند.
- تمامی رکوردها `PENDING_REVIEW` هستند و همراه با تاریخچه تصمیم‌ها در چهار جدول جدید نگهداری می‌شوند.
- تصویب دستی در این صفحه به معنی افزودن به `knowledge_rule` یا انتشار رسمی نیست.
- تغییر جداول قدیمی، انتشار Release، انتقال داده به موتور مقررات، و تطبیق واقعی PDF در فاز بعد انجام می‌شود.

## مسیر API

- `GET /api/v1/knowledge/admin/ai-import/instructions?provider=ChatGPT`
- `POST /api/v1/knowledge/admin/ai-import/packages` — فرم multipart با نام `file`
- `GET /api/v1/knowledge/admin/ai-import/packages`
- `GET /api/v1/knowledge/admin/ai-import/packages/{batchId}/documents`
- `GET /api/v1/knowledge/admin/ai-import/packages/{batchId}/items?type=RULE|SEGMENT`
- `POST /api/v1/knowledge/admin/ai-import/packages/{batchId}/items/{id}/review`
- `GET /api/v1/knowledge/admin/ai-import/packages/{batchId}/items/{id}/history`

همه endpointها نیازمند دسترسی `USER_MANAGE` هستند؛ در کنار حفاظت فعلی احراز هویت و CSRF اپلیکیشن.

## مسیر توسعه بعدی

اتصال صرفاً رکوردهای تأییدشده به نسخه جدید `structured_knowledge_version` و سند منبع قابل‌اثبات، آزمون تطبیق PDF، مسیر rollback، و انتشار فقط پس از کنترل انسانی مستقل. نسخه اول آگاهانه **ورود مستقیم AI به قوانین اجرایی** ندارد.
