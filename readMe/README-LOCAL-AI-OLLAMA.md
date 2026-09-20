# راه‌اندازی هوش مصنوعی محلی ساخت‌یار با Ollama

این Patch اتصال `PersianAgent` به مدل محلی را اضافه می‌کند. تمام تحلیل زبانی روی سیستم/سرور شما اجرا می‌شود و محاسبات قطعی مالی، ساخت و قیمت همچنان داخل Java باقی می‌مانند.

## 1) نصب Ollama روی Windows

PowerShell:

```powershell
irm https://ollama.com/install.ps1 | iex
```

یا Installer رسمی Windows را نصب کنید.

بررسی نصب:

```powershell
ollama --version
```

## 2) دانلود مدل پیشنهادی سبک

```powershell
ollama pull qwen3.5:4b
```

برای سیستم قوی‌تر می‌توانید مدل بزرگ‌تر انتخاب کنید و مقدار `OLLAMA_MODEL` را تغییر دهید.

بررسی مدل‌ها:

```powershell
ollama list
```

## 3) اجرای Ollama روی سیستم توسعه

### حالت عادی

```powershell
ollama run qwen3.5:4b --think=false
```

`--think=false` باعث می‌شود مدل‌های Qwen 3.x متن طولانی Thinking/Reasoning را چاپ نکنند و فقط پاسخ نهایی را تولید کنند. Backend ساخت‌یار نیز همین رفتار را با `OLLAMA_THINK=false` اعمال می‌کند.

### حالت CPU-only برای سیستم‌هایی که CUDA خطا می‌دهد

اگر خطاهایی مانند `CUDA error: device kernel image is invalid` دیدید، ابتدا Ollama در حال اجرا را ببندید و سپس از اسکریپت پروژه استفاده کنید:

```powershell
cd D:\ChatGPT_Projects\SakhtYar\source
.\scripts\start-ollama-local-cpu.ps1
```

این اسکریپت این تنظیمات را اعمال می‌کند:

```text
CUDA_VISIBLE_DEVICES=-1
OLLAMA_LLM_LIBRARY=cpu_avx2
OLLAMA_NO_CLOUD=true
```

و Ollama را روی CPU در `127.0.0.1:11434` اجرا می‌کند. پنجره را باز نگه دارید.

در PowerShell دوم مدل را تست کنید:

```powershell
ollama run qwen3.5:4b --think=false
```

یا API:

```powershell
Invoke-RestMethod http://localhost:11434/api/tags
```

## 4) اجرای Backend از IntelliJ

حالت پیش‌فرض برای اجرای مستقیم Backend:

```text
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen3.5:4b
OLLAMA_THINK=false
```

سپس:

```powershell
cd D:\ChatGPT_Projects\SakhtYar\source\backend
mvn clean install
```

و `SakhtYarApplication` را Run کنید.

## 5) اگر Backend با Docker Compose اجرا می‌شود

`docker-compose.yml` آدرس زیر را به Backend می‌دهد:

```text
http://host.docker.internal:11434
```

بنابراین Ollama می‌تواند روی Windows Host اجرا شود و Backend داخل Container به آن متصل شود.

## 6) تست وضعیت AI در ساخت‌یار

بعد از Login:

```text
GET /api/v1/agents/ai/status
```

انتظار:

```json
{
  "enabled": true,
  "provider": "ollama",
  "model": "qwen3.5:4b",
  "available": true
}
```

اگر `available=false` بود:

1. `ollama list` را بررسی کنید.
2. مطمئن شوید `qwen3.5:4b` دانلود شده است.
3. آدرس `OLLAMA_BASE_URL` را بررسی کنید.
4. اگر Backend داخل Docker است، از `host.docker.internal` استفاده کنید.

## 7) تست PersianAgent بدون parameters دستی

```javascript
fetch('/api/v1/agents/chat', {
  method: 'POST',
  credentials: 'include',
  headers: {'Content-Type': 'application/json'},
  body: JSON.stringify({
    message: 'زمینم 300 متره، سطح اشغال 60 درصده، 5 طبقه مجازه، مشاعات 20 درصده، قیمت زمین متری 350 میلیون تومان، هزینه ساخت متری 45 میلیون تومان و قیمت فروش متری 220 میلیون تومنه؛ مشارکتش رو بررسی کن',
    parameters: {}
  })
}).then(r => r.json()).then(console.log)
```

در `results.PERSIAN.data` باید ببینید:

```text
parserMode = LOCAL_AI
aiProvider = ollama
aiModel = qwen3.5:4b
```

و `normalizedParameters` باید پارامترهای صریح متن را شامل شود.

## 8) Fallback امن

اگر Ollama خاموش یا مدل حذف شده باشد، Backend همچنان بالا می‌آید. `PersianAgent` به parser قواعدی برمی‌گردد و در `warnings` علت را اعلام می‌کند.

## 9) اصل امنیت داده

مدل حق ندارد قیمت بازار، ضابطه شهرداری یا عددی را که کاربر نگفته است حدس بزند. Prompt این محدودیت را صریح اعمال می‌کند و خروجی مدل نیز قبل از ورود به Workflow به لیست محدود فیلدهای مجاز محدود می‌شود.


## 10) چرا Thinking خاموش است؟

`PersianAgent` از مدل برای درک زبان و استخراج JSON استفاده می‌کند، نه برای انجام محاسبات تجاری. بنابراین خروجی reasoning مدل هم زمان پاسخ را زیاد می‌کند و هم parsing خروجی JSON را دشوارتر می‌کند.

در درخواست `/api/chat` فیلد زیر ارسال می‌شود:

```json
{
  "think": false,
  "format": "json"
}
```

در صورت نیاز آزمایشی می‌توان با Environment Variable زیر آن را روشن کرد، ولی برای محیط عادی ساخت‌یار توصیه نمی‌شود:

```text
OLLAMA_THINK=true
```

## رفع خطای Encoding در IntelliJ روی Windows

اگر هنگام Build/Run در IntelliJ خطاهایی مانند موارد زیر مشاهده شد:

```text
illegal character: '\u00b0'
unclosed character literal
```

و خطا به بخش نرمال‌سازی اعداد فارسی در `PersianAgent.java` اشاره داشت، علت معمولاً تفاوت Encoding کامپایلر IDE با UTF-8 است.

نسخه v3 فایل‌های Java جدید AI را برای کاراکترهای غیر ASCII به صورت Java Unicode Escape نگهداری می‌کند تا حتی در صورت استفاده IDE از Windows-1252 نیز کامپایل پایدار بماند.

همچنان توصیه می‌شود در IntelliJ مسیر زیر روی UTF-8 تنظیم شود:

```text
Settings -> Editor -> File Encodings
Global Encoding: UTF-8
Project Encoding: UTF-8
Default encoding for properties files: UTF-8
```

پس از تغییر Encoding:

```text
File -> Invalidate Caches -> Invalidate and Restart
```

و سپس Maven را Reload و پروژه را مجدداً Build کنید.

## Auto-start Ollama from Java (v4)

When SakhtYar Backend is run directly from IntelliJ/Java on Windows, the backend
can now own the Ollama server lifecycle. Manual `ollama serve` is no longer
required.

Startup flow:

```text
SakhtYarApplication starts
  -> check http://127.0.0.1:11434/api/tags
  -> if already running: reuse it
  -> otherwise: locate ollama.exe
  -> start `ollama serve`
  -> apply CPU/no-cloud environment settings
  -> wait until /api/tags responds
  -> continue normal AI requests
```

Default local settings:

```yaml
app:
  ai:
    ollama:
      base-url: http://127.0.0.1:11434
      auto-start: true
      force-cpu: true
      llm-library: cpu_avx2
      no-cloud: true
      stop-on-shutdown: true
      startup-timeout-seconds: 30
```

The executable is discovered in this order:

1. `OLLAMA_EXECUTABLE` if explicitly configured.
2. `%LOCALAPPDATA%\\Programs\\Ollama\\ollama.exe`.
3. `%LOCALAPPDATA%\\Ollama\\ollama.exe`.
4. `%ProgramFiles%\\Ollama\\ollama.exe`.
5. `ollama.exe` from `PATH`.

If Ollama was already running before SakhtYar started, SakhtYar will not kill
that external process on shutdown. If SakhtYar started Ollama itself, it stops
that child process when Spring Boot shuts down.

For Docker Compose, `OLLAMA_AUTO_START=false` is forced because the Linux
container cannot start a Windows host process. In Docker mode Ollama must run on
the host and is reached via `host.docker.internal`.

## Performance tuning profile used by SakhtYar

SakhtYar keeps the `qwen3.5:4b` model and applies tuning from Java:

```text
OLLAMA_CONTEXT_LENGTH=2048
OLLAMA_MAX_PREDICT_TOKENS=128
OLLAMA_NUM_BATCH=128
OLLAMA_NUM_THREADS=0
OLLAMA_USE_MMAP=true
OLLAMA_KEEP_ALIVE=-1
OLLAMA_NUM_PARALLEL=1
OLLAMA_MAX_LOADED_MODELS=1
OLLAMA_PRELOAD_MODEL=true
```

`OLLAMA_NUM_THREADS=0` asks Java to detect physical Windows CPU cores once at startup. Set a positive number to override it.

The Backend logs timing after every AI request, including total time, model load time, prompt evaluation time, generation time, token counts, thread count and context size.
