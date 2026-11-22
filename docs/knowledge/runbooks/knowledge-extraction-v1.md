# SakhtYar Knowledge Extraction Runbook v1.0

## هدف
این Runbook برای ساخت یا به‌روزرسانی نسخه‌ای Knowledge Dataset ساخت‌یار از منابع داخلی Repository و بعداً crawlerها و APIهای رسمی است.

## منبع اصلی
- Repository: `HAminzadeh/SakhtYar`
- Branch: `v2`
- Source Path: `DocumentationOfLawsAndRegulations`

## اصل مهم
تحلیل سنگین محتوا باید خارج از اجرای محلی SakhtYar انجام شود.
اجرای محلی نباید OCR، LLM classification، LLM summarization یا embedding generation انجام دهد.
فقط باید بسته آماده را validate و import کند.