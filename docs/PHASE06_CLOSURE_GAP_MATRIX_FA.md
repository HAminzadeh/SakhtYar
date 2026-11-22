# فاز ۰۶ — ماتریس شکاف قبل/بعد (مبنای GitHub v2)

HEAD در زمان ممیزی: `ff66065dcbb5bcdf383b32e798fe227f0841e370`؛ این SHA مبنای فایل‌های هدف است، نه الزام ثابت برای HEAD آینده.

| موضوع | وضعیت قبل و شاهد | شکاف | اصلاح فاز ۰۶ | خطر REAL | پذیرش بعد از بسته |
|---|---|---|---|---|---|
| AI provider | `AiGateway` و `OllamaAiModelProvider` نقش TESTER را منع می‌کنند | Fake deterministic و Tool Allowlist مستقل کامل نبود | `Phase06FakeAiModelProvider` با ورودی محدود، Tool Allowlist، Retry/Timeout و Circuit Breaker | اتصال REAL توسط Fake وجود ندارد؛ در Production Autowire نمی‌شود | Java Unit / SOURCE_ONLY؛ اتصال سراسری TEST هنوز BLOCKED |
| AI error privacy | Ollama و Registry بخشی از Response body/URL/Exception را به خطا برمی‌گرداندند | افشای Token یا متن حساس در Error | حذف متن Response، Base URL و پیام Exception از خطاهای مربوط | بدون SQL | JUnit و بررسی سورس؛ تست HTTP زنده NOT_RUN |
| Agent audit | `AgentBus` متن exception هنگام اختلال Audit را با Stacktrace ثبت می‌کرد | نشت احتمالی | ثبت فقط نوع Exception | هیچ PII خام | SOURCE_ONLY |
| Intelligence Workflow | DAG max32 و unknown-component guard موجود بود | متن Exception از Component به DB/API می‌رفت | کد خطای ثابت به‌جای متن خطا | بدون Migration | SOURCE_ONLY و Maven JUnit |
| Browser | login-shell smoke، trace/video/screenshot خاموش | Harness دو Workspace و Mock HTTP نبود | تست Playwright مصنوعی، RTL، Mobile/Desktop، deny external | فقط route mock، بدون credential | CI پس از Push؛ Windows محلی NOT_RUN |
| Monitoring/Notification | Outbox موجود `REAL` و ROLE_TESTER guard؛ `Phase02OutboundGuard` موجود | Fake Outbox/Feed، Cooldown، Retry و Scoped Teardown نبود | `Phase06FakeNotificationOutbox` و `Phase06FakeMarketFeed` در حافظه | بدون Provider، DB یا گیرنده حقیقی | Java Unit؛ DB-level policy و Trigger زنده BLOCKED |
| Config | `PlatformConfigService` Draft/Publish دارد، Secret Reveal/Activation واقعی است | Sandbox و Separation of Duties برای تست بدون DB نبود | `Phase06ConfigSandbox` in-memory: Draft/Review/Publish/Rollback، Hash و جلوگیری از Secret | تغییر Config REAL صفر | Java Unit؛ Restart/DB persistence BLOCKED |
| Release Gate | `release_gate.mjs` صرفاً ساختار JSON را ارزیابی می‌کرد | SHA جعلی و مسیر ساختگی می‌توانستند از Structural PASS عبور کنند | `proof_verifier.mjs` + `release_approval.mjs` با ۹۶ فایل واقعی و HMAC مخفی CI، زمان، SHA و Workspace/Principal | Release بدون امضا و شاهد BLOCKED | تست ۱۴ حالته؛ Workflow بعد از Push NOT_RUN |
| TEST isolation | Phase02 Outbound deny و Fixture blocked، Core limited role path موجود | برای ۲۳ سناریوی دیگر ایزولاسیون اثبات نشده | Reuse کنترل Phase02؛ هیچ مسیر جایگزین REAL تعریف نشد | **بدون Clone، SQL، Role، Seed، Cleanup یا Migration** | Unit/SOURCE_ONLY؛ Full Business E2E همچنان BLOCKED |
| Build/CI | CI v2، Phase06 workflow فعال و موفق برای HEAD قبلی | Browser synthetic و Verifier مستقل داخل CI نبود | افزودن Browser synthetic job و Verifier offline، Release جدای CI | CI این Patch دیتابیس ایجاد نمی‌کند؛ CI اصلی بدون تغییر باقی است | GitHub Actions پس از Commit/Push |

## نکته در مورد CI موجود

Workflow اصلی `ci.yml` از قبل یک سرویس PostgreSQL دورریختنی برای اجرای Flyway در GitHub Actions دارد. بسته فاز ۰۶ **به این Workflow یا سرویس دست نمی‌زند**، هیچ Clone یا کپی از REAL نمی‌سازد و هیچ SQL جدیدی اجرا نمی‌کند. Workflow اضافه‌شده فقط آزمون‌های Synthetic/Offline را اجرا می‌کند.

## محدودیت‌ها

- `Phase06ConfigSandbox` وضعیت پایداری در DB ندارد و جایگزین Config واقعی نیست.
- `Phase06FakeNotificationOutbox` هیچ ورودی در جدول واقعی `notification_outbox` نمی‌نویسد.
- وجود TEST GUC به‌تنهایی اثبات Role محدود نیست؛ تست DB-level به دلیل محدودیت عملیات دیتابیس BLOCKED باقی می‌ماند.
- `release_approval.mjs` افزون بر شواهد ساختاری، به امضاکننده مستقل مورداعتماد نیاز دارد. شاهد ساختگی آزمون JUnit/Node مجوز انتشار نیست.
