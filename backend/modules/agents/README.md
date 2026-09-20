# SakhtYar Agents Module

ماژول `sakhtyar-agents` لایه هماهنگی عامل‌های هوشمند ساخت‌یار است.

## اصل معماری

Agentها مستقیماً یکدیگر را Inject نمی‌کنند. ارتباط آن‌ها از مسیر زیر انجام می‌شود:

```text
Agent -> AgentExecutionContext -> AgentBus -> AgentRegistry -> Target Agent
```

- **Agent Bus (گذرگاه عامل‌ها):** مسیر استاندارد فراخوانی Agent دیگر.
- **Agent Registry (دفتر ثبت عامل‌ها):** نگاشت نوع Agent به پیاده‌سازی Spring آن.
- **Orchestrator (هماهنگ‌کننده):** جریان چندمرحله‌ای یک درخواست کاربر را اجرا می‌کند.
- **Tool (ابزار قطعی):** عملیات محاسباتی یا سرویس‌های واقعی که نباید توسط مدل زبانی حدس زده شوند.
- **AI Provider (تأمین‌کننده مدل):** فقط برای فهم/تولید زبان استفاده می‌شود؛ منطق قطعی کسب‌وکار داخل Java باقی می‌ماند.

## Agentهای موجود

- `PERSIAN` — فهم فارسی، تشخیص هدف و استخراج پارامترهای اولیه با مدل محلی و fallback قواعدی.
- `PROPERTY` — اطلاعات ملک و اتصال به `PropertyService`.
- `MUNICIPALITY` — استانداردسازی ضوابط شهرسازی بدون حدس زدن مقررات.
- `VALUATION` — قیمت‌گذاری بر اساس قیمت صریح یا املاک مشابه.
- `CONSTRUCTION` — محاسبه سطح اشغال، زیربنا و متراژ قابل فروش.
- `FINANCIAL` — تحلیل اقتصادی و ROI و استفاده از `VALUATION` و `CONSTRUCTION`.
- `CONTRACT` — کنترل ساختاری بندهای اصلی قرارداد.
- `LEGAL` — هشدارهای حقوقی پایه و استفاده از `CONTRACT`.
- `MATCHING` — تطبیق سازنده با پروژه بر اساس معیارهای شفاف.
- `RESEARCH` — تجمیع خروجی Agentهای تخصصی و نقطه توسعه منابع بیرونی.

## AI محلی

پیاده‌سازی فعلی از Ollama استفاده می‌کند و Provider آن قابل تعویض است:

```text
PersianAgent
    -> AiModelRegistry
        -> AiModelProvider
            -> OllamaAiModelProvider
                -> http://localhost:11434
```

اگر Ollama یا مدل در دسترس نباشد، Backend از کار نمی‌افتد و `PersianAgent` به تحلیل قواعدی قبلی برمی‌گردد.

### تنظیمات

```yaml
app:
  ai:
    enabled: true
    provider: ollama
    ollama:
      base-url: http://localhost:11434
      model: qwen3.5:4b
      temperature: 0.1
      timeout-seconds: 90
```

### API وضعیت AI

```text
GET /api/v1/agents/ai/status
```

نمونه پاسخ:

```json
{
  "enabled": true,
  "provider": "ollama",
  "model": "qwen3.5:4b",
  "available": true,
  "message": "Provider محلی و مدل تنظیم‌شده در دسترس هستند."
}
```

## API اصلی Agentها

```text
POST /api/v1/agents/chat
GET  /api/v1/agents
GET  /api/v1/agents/ai/status
```

با AI محلی کاربر می‌تواند به‌جای JSON دستی، متن طبیعی بدهد:

```json
{
  "message": "زمینم 300 متره، سطح اشغال 60 درصده، 5 طبقه مجازه، مشاعات 20 درصده، قیمت زمین متری 350 میلیون تومان، هزینه ساخت متری 45 میلیون و فروش متری 220 میلیونه؛ مشارکتش رو حساب کن",
  "parameters": {}
}
```

`PersianAgent` فقط داده‌های صریح متن را استخراج می‌کند. مقادیر محاسباتی توسط Agentهای Java تولید می‌شوند.

## اولویت داده‌ها

مقادیر ساختاریافته‌ای که Caller در `parameters` ارسال می‌کند از خروجی مدل معتبرتر هستند و توسط مدل overwrite نمی‌شوند.


## تنظیم Thinking در Ollama

مدل‌های Qwen 3.x ممکن است reasoning جداگانه تولید کنند. برای PersianAgent به این متن نیاز نداریم؛ بنابراین Provider به صورت پیش‌فرض `think=false` می‌فرستد. این رفتار با `OLLAMA_THINK=false` قابل تنظیم است.

## Ollama process lifecycle

`OllamaProcessManager` automatically starts the local Ollama API when the
Backend runs directly on Windows and the API is not already reachable. It also
applies configured CPU/no-cloud environment variables to the child process.
The manager never stops an Ollama instance it did not start itself.

## Local Ollama performance profile (v5)

The Java provider now keeps `qwen3.5:4b` unchanged and tunes runtime behavior:

- `num_ctx=2048` limits context allocation for short Persian parsing tasks.
- `num_predict=128` caps the JSON response length.
- `num_batch=128` controls prompt batching.
- `num_thread=0` means Java auto-detects physical CPU cores on Windows.
- `use_mmap=true` keeps model mapping efficient.
- `keep_alive=-1` keeps the model resident while Ollama is running.
- `OLLAMA_NUM_PARALLEL=1` and `OLLAMA_MAX_LOADED_MODELS=1` avoid RAM pressure on the development laptop.
- Model preload runs during Backend startup, so the first user request avoids most model-load latency.
- The provider uses a compact JSON schema and a shorter Persian parser prompt.

All values remain configurable through `app.ai.ollama.*` / environment variables.
