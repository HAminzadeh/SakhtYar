# راه‌اندازی Ollama به‌صورت Local برای SakhtYar

این سند مراحل نصب، پیکربندی، تست و اتصال **Ollama** به ماژول `agents` در Backend پروژه **SakhtYar** را توضیح می‌دهد.

> **Ollama**: نرم‌افزاری برای اجرای مدل‌های زبانی بزرگ به‌صورت محلی روی سیستم یا سرور، بدون نیاز به ارسال درخواست به سرویس هوش مصنوعی ابری.
>
> **Local AI**: هوش مصنوعی محلی؛ یعنی مدل روی سخت‌افزار خودمان اجرا می‌شود.
>
> **LLM (Large Language Model)**: مدل زبانی بزرگ؛ مدلی که متن را می‌فهمد و تولید می‌کند.

---

## 1. معماری استفاده از Ollama در SakhtYar

در SakhtYar، مدل هوش مصنوعی نباید محاسبات مالی، شهرسازی یا مهندسی را انجام دهد. وظیفه مدل محلی فقط تحلیل زبان طبیعی، مخصوصاً متن فارسی، و تبدیل آن به داده ساختاریافته است.

جریان اصلی:

```text
کاربر
  ↓
AgentController
  ↓
PersianAgent
  ↓
AiModelProvider
  ↓
OllamaAiModelProvider
  ↓
Ollama Local API
  ↓
Qwen Model
  ↓
Structured JSON
  ↓
AgentOrchestrator
  ↓
Agentهای تخصصی Java
```

نمونه متن کاربر:

```text
زمینم 300 متره، سطح اشغال 60 درصده، 5 طبقه مجازه،
مشاعات 20 درصده، قیمت زمین متری 350 میلیون تومنه،
ساخت متری 45 میلیون و فروش متری 220 میلیونه.
مشارکتش رو بررسی کن.
```

خروجی مورد انتظار از `PersianAgent`:

```json
{
  "intent": "PARTNERSHIP_ANALYSIS",
  "parameters": {
    "landAreaM2": 300,
    "coverageRatio": 60,
    "allowedResidentialFloors": 5,
    "commonAreaRatio": 20,
    "landPricePerM2": 350000000,
    "constructionCostPerM2": 45000000,
    "salePricePerM2": 220000000,
    "currencyUnit": "TOMAN"
  }
}
```

بعد از این مرحله، محاسبات اصلی توسط Java انجام می‌شود.

---

## 2. پیش‌نیازها

برای محیط توسعه Windows:

- Windows 10 یا بالاتر
- دسترسی به PowerShell یا Windows Terminal
- فضای کافی برای دانلود مدل
- Backend پروژه SakhtYar با Java 21
- Maven
- دسترسی محلی به پورت `11434`

مدل پیشنهادی فعلی پروژه:

```text
qwen3.5:4b
```

این مدل در Ollama حدود 3.4GB حجم دارد و برای شروع توسعه محلی گزینه مناسبی است.

---

## 3. نصب Ollama روی Windows

### روش پیشنهادی پروژه: Winget

PowerShell را باز کنید و اجرا کنید:

```powershell
winget install Ollama.Ollama
```

بعد از پایان نصب، پنجره PowerShell را کامل ببندید و یک PowerShell جدید باز کنید.

بررسی نصب:

```powershell
ollama --version
```

اگر نسخه Ollama نمایش داده شد، نصب موفق است.

---

## 4. روش جایگزین نصب

اگر `winget` در دسترس نیست، از Installer رسمی Windows استفاده کنید:

```text
https://ollama.com/download/windows
```

فایل Installer معمولاً با نام زیر ارائه می‌شود:

```text
OllamaSetup.exe
```

پس از نصب، Terminal را بسته و دوباره باز کنید.

### نکته درباره install.ps1

در برخی شبکه‌ها دستور زیر ممکن است با خطای `403 Forbidden` مواجه شود:

```powershell
irm https://ollama.com/install.ps1 | iex
```

در این حالت از `winget` یا `OllamaSetup.exe` استفاده کنید.

---

## 5. رفع مشکل شناسایی نشدن دستور ollama

اگر بعد از نصب با این خطا روبه‌رو شدید:

```text
ollama : The term 'ollama' is not recognized
```

ابتدا PowerShell را ببندید و دوباره باز کنید.

اگر مشکل ادامه داشت، مسیر مستقیم را تست کنید:

```powershell
& "$env:LOCALAPPDATA\Programs\Ollama\ollama.exe" --version
```

اگر این دستور کار کرد، خود Ollama نصب شده ولی `PATH` هنوز به‌روزرسانی نشده است.

### اضافه کردن موقت مسیر در Session جاری

```powershell
$env:Path += ";$env:LOCALAPPDATA\Programs\Ollama"
```

بعد:

```powershell
ollama --version
```

### اضافه کردن دائمی به User PATH

```powershell
$ollamaPath = "$env:LOCALAPPDATA\Programs\Ollama"
$currentUserPath = [Environment]::GetEnvironmentVariable("Path", "User")

if (($currentUserPath -split ';') -notcontains $ollamaPath) {
    [Environment]::SetEnvironmentVariable(
        "Path",
        "$currentUserPath;$ollamaPath",
        "User"
    )
}
```

بعد Terminal را ببندید و دوباره باز کنید.

---

## 6. محل فایل‌های Ollama در Windows

مسیر باینری‌های Ollama:

```text
%LOCALAPPDATA%\Programs\Ollama
```

مسیر Logها:

```text
%LOCALAPPDATA%\Ollama
```

مسیر پیش‌فرض Modelها:

```text
%USERPROFILE%\.ollama\models
```

برای مشاهده Logها:

```powershell
explorer "$env:LOCALAPPDATA\Ollama"
```

---

## 7. اجرای Ollama

در نصب استاندارد Windows، Ollama معمولاً بعد از اجرا به‌صورت Background در حال کار است.

> **Background**: اجرای برنامه در پس‌زمینه بدون باز بودن پنجره اصلی.

برای بررسی API:

```powershell
Invoke-RestMethod http://localhost:11434/api/tags
```

اگر پاسخ JSON دریافت کردید، سرویس Ollama در حال اجرا است.

در صورت نیاز می‌توان Ollama را دستی اجرا کرد:

```powershell
ollama serve
```

> **API (Application Programming Interface)**: رابط نرم‌افزاری که Backend از طریق آن با Ollama ارتباط برقرار می‌کند.

آدرس پیش‌فرض API:

```text
http://localhost:11434
```

---

## 8. دانلود مدل پیشنهادی SakhtYar

مدل پیشنهادی:

```powershell
ollama pull qwen3.5:4b
```

> **pull**: دانلود مدل از مخزن Ollama و ذخیره آن روی سیستم محلی.

بررسی مدل‌های نصب‌شده:

```powershell
ollama list
```

باید مدلی مشابه زیر در خروجی وجود داشته باشد:

```text
qwen3.5:4b
```

---

## 9. تست مستقیم مدل

مدل را اجرا کنید:

```powershell
ollama run qwen3.5:4b
```

مثلاً این متن را وارد کنید:

```text
یک زمین 300 متری دارم، سطح اشغال 60 درصد و 5 طبقه مجاز است.
```

برای خروج از محیط تعاملی:

```text
Ctrl + D
```

یا:

```text
/bye
```

---

## 10. تست API مدل از PowerShell

ابتدا Body درخواست را بسازید:

```powershell
$body = @{
    model = "qwen3.5:4b"
    messages = @(
        @{
            role = "user"
            content = "یک زمین 300 متری دارم"
        }
    )
    stream = $false
} | ConvertTo-Json -Depth 10
```

سپس درخواست را ارسال کنید:

```powershell
Invoke-RestMethod `
    -Method Post `
    -Uri http://localhost:11434/api/chat `
    -ContentType "application/json" `
    -Body $body
```

اگر پاسخ دریافت شد، Ollama و مدل هر دو آماده هستند.

---

## 11. تنظیمات SakhtYar برای اجرای Backend از IntelliJ

در حالت توسعه محلی، Backend و Ollama هر دو روی Windows اجرا می‌شوند.

مقادیر موردنیاز:

```text
SAKHTYAR_AI_ENABLED=true
SAKHTYAR_AI_PROVIDER=ollama
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen3.5:4b
OLLAMA_TEMPERATURE=0.1
OLLAMA_TIMEOUT_SECONDS=90
```

توضیح متغیرها:

| متغیر | توضیح فارسی |
|---|---|
| `SAKHTYAR_AI_ENABLED` | فعال یا غیرفعال کردن هوش مصنوعی محلی |
| `SAKHTYAR_AI_PROVIDER` | مشخص می‌کند Provider هوش مصنوعی `ollama` باشد |
| `OLLAMA_BASE_URL` | آدرس API محلی Ollama |
| `OLLAMA_MODEL` | نام مدل مورد استفاده |
| `OLLAMA_TEMPERATURE` | میزان آزادی مدل در تولید پاسخ؛ مقدار پایین برای استخراج دقیق‌تر داده مناسب‌تر است |
| `OLLAMA_TIMEOUT_SECONDS` | حداکثر زمان انتظار Backend برای پاسخ مدل |

> **Provider**: لایه اتصال پروژه به یک ارائه‌دهنده مدل هوش مصنوعی.
>
> **Temperature**: پارامتری برای کنترل میزان تنوع و خلاقیت پاسخ مدل.

---

## 12. تنظیم در application.yml

تنظیمات فعلی ماژول AI باید ساختاری مشابه زیر داشته باشد:

```yaml
app:
  ai:
    enabled: ${SAKHTYAR_AI_ENABLED:true}
    provider: ${SAKHTYAR_AI_PROVIDER:ollama}

    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      model: ${OLLAMA_MODEL:qwen3.5:4b}
      temperature: ${OLLAMA_TEMPERATURE:0.1}
      timeout-seconds: ${OLLAMA_TIMEOUT_SECONDS:90}
```

توصیه می‌شود مقادیر محیطی واقعی داخل Source Commit نشوند و در `.env` یا تنظیمات Run Configuration قرار بگیرند.

---

## 13. Build پروژه بعد از تنظیم Ollama

از مسیر Backend:

```powershell
cd D:\ChatGPT_Projects\SakhtYar\source\backend
```

Build:

```powershell
mvn clean install
```

خروجی مورد انتظار:

```text
SakhtYar Agents ........ SUCCESS
SakhtYar Application ... SUCCESS

BUILD SUCCESS
```

بعد `SakhtYarApplication` را از IntelliJ اجرا کنید.

---

## 14. تست وضعیت AI در Backend

بعد از Login در SakhtYar:

```text
GET /api/v1/agents/ai/status
```

نمونه پاسخ سالم:

```json
{
  "enabled": true,
  "provider": "ollama",
  "model": "qwen3.5:4b",
  "available": true,
  "message": "Provider محلی و مدل تنظیم‌شده در دسترس هستند."
}
```

مقدار اصلی برای بررسی:

```text
available = true
```

اگر `false` بود، بخش Troubleshooting این سند را بررسی کنید.

---

## 15. تست PersianAgent بدون Parameters دستی

بعد از Login، در Console مرورگر:

```javascript
fetch('/api/v1/agents/chat', {
  method: 'POST',
  credentials: 'include',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    message: `
      زمینم 300 متره،
      سطح اشغال 60 درصده،
      5 طبقه مجازه،
      مشاعات 20 درصده،
      قیمت زمین متری 350 میلیون تومنه،
      هزینه ساخت متری 45 میلیون تومنه،
      قیمت فروش هم متری 220 میلیونه.
      مشارکتش رو بررسی کن.
    `,
    parameters: {}
  })
})
.then(r => r.json())
.then(console.log)
```

در خروجی:

```text
results.PERSIAN.data.parserMode
```

باید مقدار زیر را داشته باشد:

```text
LOCAL_AI
```

همچنین:

```text
results.PERSIAN.data.aiProvider = ollama
results.PERSIAN.data.aiModel = qwen3.5:4b
```

---

## 16. Fallback در صورت قطع بودن Ollama

SakhtYar نباید فقط به Ollama وابسته باشد.

اگر Ollama خاموش باشد یا مدل در دسترس نباشد:

```text
PersianAgent
    ↓
Ollama unavailable
    ↓
Rule Parser
```

در این وضعیت Parser قواعدی قبلی استفاده می‌شود.

نمونه:

```text
parserMode = RULE_FALLBACK
```

> **Fallback**: مسیر جایگزین که هنگام خراب بودن سرویس اصلی استفاده می‌شود.

این رفتار باعث می‌شود خاموش بودن مدل محلی باعث Down شدن Backend نشود.

---

## 17. اجرای Backend با Docker و Ollama روی Windows Host

اگر Backend داخل Docker اجرا شود ولی Ollama روی Windows Host باشد، آدرس `localhost` داخل Container به خود Container اشاره می‌کند و نه Windows Host.

در این حالت مقدار Backend باید به شکل زیر باشد:

```text
OLLAMA_BASE_URL=http://host.docker.internal:11434
```

> **Host**: سیستم عامل اصلی که Docker روی آن اجرا می‌شود.
>
> **Container**: محیط ایزوله‌ای که سرویس Backend داخل آن اجرا می‌شود.

### نکته مهم درباره دسترسی شبکه

Ollama به‌صورت پیش‌فرض روی `127.0.0.1:11434` گوش می‌دهد. اگر Container نتوانست به سرویس Host وصل شود، ممکن است لازم باشد Ollama روی آدرس شبکه قابل دسترس Bind شود.

در Windows:

1. Ollama را از System Tray کاملاً Quit کنید.
2. وارد `Environment Variables` شوید.
3. متغیر زیر را برای User ایجاد کنید:

```text
OLLAMA_HOST=0.0.0.0:11434
```

4. Ollama را دوباره از Start Menu اجرا کنید.

> **Bind**: مشخص کردن آدرس شبکه‌ای که یک سرویس روی آن درخواست‌ها را دریافت می‌کند.

### هشدار امنیتی

تنظیم `0.0.0.0` می‌تواند Ollama را برای سایر دستگاه‌های شبکه نیز قابل دسترس کند. این تنظیم فقط در شبکه امن توسعه انجام شود و Firewall محدود باشد.

---

## 18. تست اتصال Docker به Ollama

اگر Backend داخل Docker است، ابتدا از روی Windows تست کنید:

```powershell
Invoke-RestMethod http://localhost:11434/api/tags
```

سپس از داخل Container Backend:

```bash
curl http://host.docker.internal:11434/api/tags
```

اگر `curl` داخل Image موجود نبود، می‌توان از ابزارهای شبکه یا تست Status Endpoint خود SakhtYar استفاده کرد.

---

## 19. تغییر محل ذخیره مدل‌ها

مدل‌ها فضای زیادی مصرف می‌کنند.

برای تغییر محل Modelها، متغیر محیطی زیر قابل استفاده است:

```text
OLLAMA_MODELS=D:\OllamaModels
```

بعد از تغییر این متغیر، Ollama را Restart کنید.

---

## 20. استفاده کاملاً Local و بدون Cloud

اگر هدف این است که فقط مدل‌های محلی استفاده شوند و قابلیت‌های Cloud مربوط به Ollama فعال نباشند، می‌توان متغیر زیر را تنظیم کرد:

```text
OLLAMA_NO_CLOUD=1
```

بعد Ollama را Restart کنید.

این تنظیم برای محیط‌هایی که محرمانگی اطلاعات اهمیت بیشتری دارد مناسب است.

---

## 21. سیاست استفاده AI در SakhtYar

مدل زبانی اجازه ندارد اطلاعات واقعی سیستم را اختراع کند.

AI فقط برای این کارها استفاده می‌شود:

```text
فهم متن فارسی
تشخیص Intent
استخراج فیلدها
نرمال‌سازی اصطلاحات
تولید توضیح قابل فهم
```

AI نباید خودش این موارد را تولید یا حدس بزند:

```text
ضابطه شهرداری
قیمت واقعی ملک
قیمت بازار
تعداد طبقات قانونی
هزینه واقعی ساخت
سود قطعی پروژه
```

این اطلاعات باید از:

```text
Database
API معتبر
کاربر
کارشناس
Toolهای Java
```

تأمین شوند.

---

## 22. اولویت داده در Agentها

ترتیب اعتبار داده در SakhtYar:

```text
1. داده قطعی Database / API
2. پارامتر صریح کاربر یا سیستم
3. داده استخراج‌شده توسط AI
4. Parser قواعدی
```

AI نباید داده قطعی سیستم را Override کند.

> **Override**: جایگزین کردن یک مقدار موجود با مقدار جدید.

---

## 23. خطاهای متداول و راه‌حل

### خطا: ollama is not recognized

راه‌حل:

```powershell
& "$env:LOCALAPPDATA\Programs\Ollama\ollama.exe" --version
```

در صورت موفق بودن، PATH را اصلاح کنید.

---

### خطا: 403 Forbidden هنگام install.ps1

به‌جای Script Installer از این روش استفاده کنید:

```powershell
winget install Ollama.Ollama
```

یا Installer رسمی Windows را دانلود کنید.

---

### خطا: API روی 11434 پاسخ نمی‌دهد

تست:

```powershell
Invoke-RestMethod http://localhost:11434/api/tags
```

اگر خطا داد:

- Ollama را از Start Menu اجرا کنید.
- System Tray را بررسی کنید.
- در صورت نیاز `ollama serve` را اجرا کنید.
- Log را بررسی کنید.

مسیر Log:

```text
%LOCALAPPDATA%\Ollama\server.log
```

---

### خطا: model not found

بررسی:

```powershell
ollama list
```

در صورت نبود مدل:

```powershell
ollama pull qwen3.5:4b
```

---

### خطا: Backend مقدار available=false برمی‌گرداند

بررسی کنید:

```text
SAKHTYAR_AI_ENABLED=true
SAKHTYAR_AI_PROVIDER=ollama
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen3.5:4b
```

و سپس:

```powershell
ollama list
```

---

### خطا: Backend داخل Docker به Ollama وصل نمی‌شود

بررسی کنید:

```text
OLLAMA_BASE_URL=http://host.docker.internal:11434
```

در صورت نیاز:

```text
OLLAMA_HOST=0.0.0.0:11434
```

و Ollama را Restart کنید.

---

### پاسخ مدل خیلی کند است

موارد زیر را بررسی کنید:

- اجرای مدل روی CPU یا GPU
- میزان RAM و VRAM آزاد
- تعداد برنامه‌های در حال اجرا
- اندازه مدل
- طول Prompt

برای سیستم ضعیف‌تر می‌توان در آینده مدل کوچک‌تری تنظیم کرد.

---

## 24. دستورهای کاربردی Ollama

نمایش نسخه:

```powershell
ollama --version
```

نمایش مدل‌ها:

```powershell
ollama list
```

دانلود مدل:

```powershell
ollama pull qwen3.5:4b
```

اجرای مدل:

```powershell
ollama run qwen3.5:4b
```

حذف مدل:

```powershell
ollama rm qwen3.5:4b
```

اجرای Server به‌صورت دستی:

```powershell
ollama serve
```

تست API:

```powershell
Invoke-RestMethod http://localhost:11434/api/tags
```

---

## 25. Checklist نهایی راه‌اندازی

قبل از تست SakhtYar، موارد زیر باید تأیید شوند:

```text
[ ] Ollama نصب شده است
[ ] ollama --version کار می‌کند
[ ] Ollama API روی 11434 پاسخ می‌دهد
[ ] qwen3.5:4b دانلود شده است
[ ] ollama list مدل را نمایش می‌دهد
[ ] مدل با ollama run تست شده است
[ ] Backend Build موفق دارد
[ ] SAKHTYAR_AI_ENABLED=true است
[ ] Provider روی ollama تنظیم شده است
[ ] /api/v1/agents/ai/status مقدار available=true می‌دهد
[ ] PersianAgent مقدار parserMode=LOCAL_AI می‌دهد
```

---

## 26. مسیر پیشنهادی این سند در Repository

پیشنهاد می‌شود این فایل در مسیر زیر نگهداری شود:

```text
docs/setup/LOCAL-OLLAMA-SETUP.md
```

یا در ساختار فعلی Repository:

```text
readMe/README-LOCAL-OLLAMA-SETUP.md
```

---

## 27. منابع رسمی

Ollama Windows:

```text
https://ollama.com/download/windows
```

مدل Qwen 3.5:

```text
https://ollama.com/library/qwen3.5
```

مستند Windows Ollama:

```text
https://github.com/ollama/ollama/blob/main/docs/windows.mdx
```

Ollama FAQ و Environment Variables:

```text
https://github.com/ollama/ollama/blob/main/docs/faq.mdx
```

---

## 28. نتیجه

بعد از اجرای صحیح این مراحل، SakhtYar می‌تواند متن فارسی کاربر را با مدل محلی تحلیل کند، بدون اینکه منطق محاسباتی پروژه به مدل زبانی وابسته شود.

معماری نهایی باید این اصل را حفظ کند:

```text
AI = فهم زبان و استخراج اطلاعات
Java = منطق قطعی و محاسبات
Database / API = منبع داده معتبر
```

این تفکیک باعث می‌شود سیستم هم قابل کنترل باشد، هم در صورت قطع Ollama از کار نیفتد، و هم در آینده بتوان مدل محلی را بدون بازنویسی Agentها تغییر داد.
