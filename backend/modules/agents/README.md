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

## Agentهای موجود

- `PERSIAN` — نرمال‌سازی فارسی، تشخیص هدف و استخراج پارامترهای اولیه.
- `PROPERTY` — اطلاعات ملک و اتصال به `PropertyService`.
- `MUNICIPALITY` — استانداردسازی ضوابط شهرسازی بدون حدس زدن مقررات.
- `VALUATION` — قیمت‌گذاری بر اساس قیمت صریح یا املاک مشابه.
- `CONSTRUCTION` — محاسبه سطح اشغال، زیربنا و متراژ قابل فروش.
- `FINANCIAL` — تحلیل اقتصادی و ROI و استفاده از `VALUATION` و `CONSTRUCTION`.
- `CONTRACT` — کنترل ساختاری بندهای اصلی قرارداد.
- `LEGAL` — هشدارهای حقوقی پایه و استفاده از `CONTRACT`.
- `MATCHING` — تطبیق سازنده با پروژه بر اساس معیارهای شفاف.
- `RESEARCH` — تجمیع خروجی Agentهای تخصصی و نقطه توسعه منابع بیرونی.

## API

```text
POST /api/v1/agents/chat
GET  /api/v1/agents
```

نمونه درخواست:

```json
{
  "caseId": "UUID-OF-CASE",
  "message": "این زمین برای مشارکت می‌صرفه؟",
  "parameters": {
    "municipalityRules": {
      "coverageRatio": 60,
      "allowedResidentialFloors": 5,
      "commonAreaRatio": 18
    },
    "landPricePerM2": 350000000,
    "constructionCostPerM2": 45000000,
    "salePricePerM2": 220000000
  }
}
```

## اتصال مدل‌های AI

رابط `AiModelProvider` برای اتصال بعدی OpenAI، Ollama یا مدل داخلی در نظر گرفته شده است. محاسبات مالی، شهرسازی و متراژ نباید داخل مدل زبانی انجام شوند.
