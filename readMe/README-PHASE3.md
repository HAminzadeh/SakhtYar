# SakhtYar Phase 3 — Persian Agent, Glossary & Orchestrator

این بسته روی آخرین وضعیت `main` در commit زیر طراحی شده است:

`655cad599d3fa296593eb32dffd7731a5297c6ae` — `Implement Ollama AI Provider`

## چه چیزی اضافه/اصلاح شده است؟

### Backend

- Persian Agent به شکل Case-aware اجرا می‌شود و خلاصه Property همان Case را به مدل می‌دهد.
- قرارداد خروجی Agentها با `schemaVersion=1.0` نسخه‌بندی شده است.
- ورودی ساختاریافته Caller همیشه از extraction مدل معتبرتر است.
- Unknown Term Flow اضافه شده است: مدل باید اصطلاح نامطمئن را در `parameters.unknownTerms` برگرداند و Orchestrator قبل از ادامه متوقف می‌شود.
- Glossary از حالت فقط in-memory خارج شده و Draft / Approve / Reject در PostgreSQL دارد.
- Conversation، Message و Agent Run در PostgreSQL ذخیره می‌شوند.
- Audit اجرای Agent شامل version، duration، input/output، call chain و نیاز به Human Approval شده است.
- Agent Registry اکنون metadata کامل‌تر شامل capability/provider/schema/timeout دارد.
- Orchestrator از اجرای تکراری غیرضروری Agentها جلوگیری می‌کند، ولی Property را بعد از extraction یک بار با effective request تازه می‌کند.
- Ratioهای خارج بازه معتبر رد می‌شوند.
- Valuation بدون منبع قیمت حدس نمی‌زند و Comparable Median را صریحاً provisional اعلام می‌کند.
- Financial Agent خروجی را سناریویی/PARTIAL نگه می‌دارد و Human Approval را الزامی اعلام می‌کند.
- Contract Agent دیگر از keyword matching نتیجه «کامل بودن قرارداد» نمی‌گیرد و فقط Structural Screening انجام می‌دهد.
- Matching Agent در صورت کمبود معیارهای پروژه نتیجه را PARTIAL اعلام می‌کند.

### Database

Migration جدید:

`V5__phase3_agents.sql`

جداول:

- `agent_conversation`
- `agent_message`
- `agent_run`
- `persian_glossary_entry`

### API

- `POST /api/v1/agents/chat`
- `GET /api/v1/agents/conversations/{conversationId}/messages`
- `GET /api/v1/agents`
- `GET /api/v1/agents/registry`
- `GET /api/v1/agents/ai/status`
- `GET /api/v1/agents/glossary`
- `GET /api/v1/agents/glossary/approved`
- `POST /api/v1/agents/glossary/drafts`
- `POST /api/v1/agents/glossary/{id}/approve`
- `POST /api/v1/agents/glossary/{id}/reject`

### Frontend

در Case Workspace تب جدید «دستیار هوشمند» اضافه شده است:

- Chat فارسی
- Conversation history
- نمایش Intent / Workflow / Status
- نمایش missing fields
- نمایش JSON/fields استخراج‌شده
- Apply-to-Case با تأیید کاربر
- Unknown term clarification
- ساخت Draft واژه‌نامه
- تأیید انسانی واژه قبل از استفاده بعدی Agent

## نصب Overlay

### Windows / PowerShell

```powershell
.\apply-phase3.ps1 -Repo "C:\path\to\SakhtYar"
```

### Linux/macOS

```bash
chmod +x ./apply-phase3.sh
./apply-phase3.sh /path/to/SakhtYar
```

## Build و تست پیشنهادی

Backend:

```bash
cd backend
mvn clean test
```

Frontend:

```bash
cd frontend
npm install
npm run build
```

سپس سرویس را با PostgreSQL بالا بیاورید تا Flyway، migration `V5` را اجرا کند.

## تست دستی Phase 3

### 1. AI status

```http
GET /api/v1/agents/ai/status
```

### 2. Chat

```json
POST /api/v1/agents/chat
{
  "caseId": "<CASE_UUID>",
  "message": "این زمین 250 متره، برش 12 متره، منطقه 5 تهرانه؛ ببین چند طبقه میشه ساخت",
  "parameters": {}
}
```

انتظار:

- `schemaVersion = 1.0`
- Intent برابر `BUILDABILITY_ANALYSIS`
- استخراج `landAreaM2=250` و `frontageM=12` و district
- اگر ضابطه معتبر سطح اشغال/طبقات وجود ندارد، Municipality/Construction باید `NEEDS_INPUT` بدهند و عدد حدس نزنند.

### 3. Unknown Term

یک اصطلاح محلی ناشناخته وارد کنید. اگر مدل آن را unknown تشخیص دهد:

- `PERSIAN.status = NEEDS_INPUT`
- `unknownTerms` پر می‌شود.
- Orchestrator Agentهای بعدی را اجرا نمی‌کند.
- در UI معنی اصطلاح را وارد کنید، Draft بسازید و سپس Approve کنید.
- درخواست را دوباره ارسال کنید؛ اصطلاح Approved دیگر نباید workflow را متوقف کند.

### 4. Apply to Case

در Assistant Panel بعد از extraction روی «اعمال فیلدهای قابل ذخیره روی ملک» کلیک کنید. فقط فیلدهای موجود در Property فعلی ذخیره می‌شوند؛ `frontageM` فعلاً در Conversation/Agent Run می‌ماند تا مدل Property در فاز شهرسازی توسعه داده شود.

## نکته GitHub

اتصال GitHub در زمان تولید این بسته امکان خواندن repository را داشت اما endpointهای write با خطای GitHub App زیر رد شدند:

`403 Resource not accessible by integration`

بنابراین فایل‌ها به صورت Overlay آماده شده‌اند و مستقیماً در repository commit نشده‌اند.
