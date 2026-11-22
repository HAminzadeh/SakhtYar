package com.sakhtyar.knowledge.application;

/** Prompts intentionally share the V1 archive schema to preserve the existing review workflow. */
public final class KnowledgeAiPromptLibrary {
    private KnowledgeAiPromptLibrary() {}

    public static String prompt(String mode) {
        return switch (mode) {
            case "LAW" -> """
                نقش شما: استخراج‌کننده دقیق مستندات قانونی برای سامانه ساخت‌یار.
                زبان خروجی فارسی؛ خروجی نهایی فقط یک ZIP با چهار فایل UTF-8 در ریشه آن است.
                این خروجی نامزد بررسی کارشناسی است و قانون معتبر یا قابل انتشار خودکار نیست.

                ۱) فقط متن خوانای منابع رسمی/قانونی پیوست را استخراج کن. هیچ حکم، ماده، تبصره، شماره ماده، عدد، تاریخ، مرجع یا صفحه اختراع نکن؛ منبع آموزشی را قانون تلقی نکن.
                ۲) مرز ماده‌ها و تبصره‌ها را رعایت کن؛ هر حکم مستقل یک رکورد مستقل. full_text باید عین متن خوانای منبع باشد، نه خلاصه یا بازنویسی. evidence_quote باید زیررشته دقیق full_text و حداقل ۸ کاراکتر باشد.
                ۳) متن خوانایی که ساختار ماده/بند آن نامعلوم است در legal_segments.jsonl با segment_type=PASSAGE قرار گیرد؛ شماره صفحه را تنها اگر واقعاً از اصل فایل معلوم است ثبت کن. موارد فاقد صفحه قابل‌راستی‌آزمایی را در گزارش برای اصلاح نگه دار و شماره ساختگی ننویس؛ بسته نامعتبر تولید نکن.
                ۴) documents.jsonl: برای هر سند یک JSON مستقل در هر سطر با فیلدها:
                   {"doc_key":"doc-001","title":"عنوان دقیق","category":"LAW","source_reference":"نام فایل منبع","authority":"مرجع رسمی یا نامعلوم","source_sha256":null}
                   category فقط LAW / EDUCATION / TECHNICAL / REFERENCE / OTHER / UNCLASSIFIED است. منبع آموزشی حتماً EDUCATION باشد؛ قانون تنها وقتی LAW است که مرجع قانونی احراز شده باشد.
                ۵) legal_segments.jsonl: هر سطر JSON با فیلدها:
                   {"doc_key":"doc-001","item_key":"seg-001","segment_type":"ARTICLE","article_label":"ماده ۱","full_text":"متن دقیق حداقل دوازده کاراکتر","evidence_quote":"عبارت دقیق هشت کاراکتری","page_from":1,"page_to":1}
                   segment_type فقط ARTICLE / NOTE / CLAUSE / PASSAGE است؛ page_from و page_to شماره صفحات واقعی و مثبت از فایل اصلی هستند.
                ۶) rules.jsonl: فقط احکام صریح مستند LAW را با همان فیلدهای legal_segments و item_key مستقل وارد کن. از استنتاج حکم، محاسبه حدس‌گونه و تبدیل جزوه آموزشی به مقرره خودداری کن.
                ۷) manifest.json دقیقاً:
                   {"schema_version":"sakhtyar-ai-knowledge-v1","instruction_version":"v52-law","source_ai":"ChatGPT","language":"fa","review_policy":"HUMAN_REQUIRED"}
                   source_ai را نام واقعی مدل استفاده‌شده قرار بده. source_sha256 فقط در صورت محاسبه واقعی هش SHA-256 فایل اصلی غیر-null باشد.
                ۸) ZIP فقط چهار فایل manifest.json, documents.jsonl, rules.jsonl, legal_segments.jsonl داشته باشد. قواعد JSONL: هر سطر یک شیء JSON کامل، بدون Markdown و بدون code fence. rules.jsonl می‌تواند خالی باشد اما باید وجود داشته باشد.
                ۹) حداکثر ۳۰۰۰ رکورد و ۲۴ مگابایت برای هر بسته؛ در صورت نیاز چند بسته جداگانه بساز. هیچ فایل منبع یا پوشه اضافی داخل ZIP قرار نده.
                ۱۰) تمام یافته‌ها نیازمند بررسی انسان‌اند؛ هیچ نتیجه‌ای را به عنوان قانون تأییدشده یا فعال ثبت نکن.
                """;
            case "EDUCATION" -> """
                نقش شما: استخراج‌کننده محتوای آموزشی و فنی برای کاتالوگ و دانش‌نامه ساخت‌یار.
                زبان خروجی فارسی؛ خروجی فقط یک ZIP شامل چهار فایل استاندارد UTF-8 در ریشه آرشیو است.
                این اسناد *آموزشی* هستند؛ نه متن قانون و نه مقررات الزام‌آور، مگر منبع اصلی و رسمی دیگری مستقلاً اثبات شود.

                ۱) جزوه‌ها، مثال‌های آموزشی، توضیحات اجرایی و مطالب مهندسی خوانای فایل‌های ارائه‌شده را به تفکیک سند و موضوع استخراج کن؛ بخش واقعاً نامفهوم یا فاقد متن قابل‌استناد را بدون حدس کنار بگذار، اما وجود نقص را در پاسخ متنی کوتاه خارج از ZIP گزارش کن.
                ۲) برای هر سند documents.jsonl یک JSON در هر خط بساز:
                   {"doc_key":"doc-001","title":"نام واقعی جزوه","category":"EDUCATION","source_reference":"نام فایل اصلی","authority":"نام مدرس یا تهیه‌کننده، یا نامعلوم","source_sha256":null}
                   اگر سند عمدتاً مرجع فنی غیرآموزشی است category=TECHNICAL یا REFERENCE باشد. دسته‌های مجاز LAW / EDUCATION / TECHNICAL / REFERENCE / OTHER / UNCLASSIFIED هستند.
                ۳) تمام مطالب قابل‌استفاده را در legal_segments.jsonl ثبت کن؛ اسم این فایل به خاطر سازگاری با سامانه ثابت مانده، اما محتوا قانون نیست. هر مفهوم، توضیح یا مثال مستقل یک رکورد:
                   {"doc_key":"doc-001","item_key":"edu-001","segment_type":"PASSAGE","article_label":"عنوان واقعی فصل یا موضوع","full_text":"متن دقیق خوانای جزوه با حداقل دوازده کاراکتر","evidence_quote":"عبارت عینی از متن با حداقل هشت کاراکتر","page_from":1,"page_to":1}
                   segment_type فقط PASSAGE یا CLAUSE برای مطلب آموزشی است. شماره صفحه را از اصل سند بگیر؛ اگر صفحه قابل تشخیص نیست شماره نساز و آن مورد را برای بازبینی جداگانه گزارش کن.
                ۴) full_text و evidence_quote عین عبارت منبع باشند؛ خلاصه‌سازیِ داخل full_text، افزودن فرمول یا عدد ناموجود، ترکیب دو بخش مستقل و انتساب حکم قانونی ممنوع است. evidence_quote باید دقیقاً در full_text یافت شود.
                ۵) rules.jsonl باید *وجود داشته باشد اما کاملاً خالی باشد*؛ از محتوای آموزشی حکم قانونی نساز.
                ۶) manifest.json دقیقاً:
                   {"schema_version":"sakhtyar-ai-knowledge-v1","instruction_version":"v52-education","source_ai":"ChatGPT","language":"fa","review_policy":"HUMAN_REQUIRED"}
                   source_ai را نام واقعی مدل بگذار. source_sha256 را تنها در صورت محاسبه واقعی هش SHA-256 فایل اصلی ثبت کن؛ وگرنه null.
                ۷) ZIP فقط manifest.json, documents.jsonl, rules.jsonl, legal_segments.jsonl در ریشه داشته باشد؛ هر فایل JSONL یک شیء JSON در هر سطر، بدون Markdown/code fence.
                ۸) سقف هر بسته ۳۰۰۰ رکورد و ۲۴ مگابایت است؛ در صورت نیاز بسته‌ها را تفکیک کن. حتی پس از ورود به سامانه، برچسب EDUCATION/TECHNICAL باید حفظ شود و نباید خودکار به LAW تبدیل شود.
                ۹) همه رکوردها نامزد بازبینی کارشناسی هستند، نه اطلاعات حقوقی قطعی.
                """;
            default -> throw new IllegalArgumentException("mode must be LAW or EDUCATION");
        };
    }
}
