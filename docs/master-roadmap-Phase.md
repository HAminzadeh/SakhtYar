# نقشه‌راه جامع توسعه SakhtYar

> **نسخه سند:** 1.0  
> **وضعیت پروژه در زمان نگارش:** Phase 0، Phase 1 و Phase 2 تکمیل شده‌اند  
> **مرجع معماری:** طراحی جامع مشارکت ساخت 02  
> **فناوری اصلی:** Java 21 + Spring Boot 4 + React/TypeScript + PostgreSQL/PostGIS + Redis + MinIO  
> **سبک معماری:** Modular Monolith با امکان اجرای مستقل Runtimeهای API / Worker / Collector / Agent

---

## 1. هدف این سند

این سند نقشه‌راه مرجع توسعه SakhtYar از شروع پروژه تا رسیدن به نسخه Production-ready را مشخص می‌کند.

هدف اصلی این است که:

- هر فاز محدوده مشخص داشته باشد.
- خروجی هر فاز قابل اجرا و تست باشد.
- وابستگی بین فازها روشن باشد.
- Backend، Frontend، Database، Agentها و Integrations همزمان و هماهنگ رشد کنند.
- از ایجاد معماری سنگین و زودهنگام جلوگیری شود.
- تمام تصمیم‌های مهم قابل Audit و قابل Trace باشند.
- منطق حقوقی، مالی، شهرسازی و ارزش‌گذاری از ابتدا با Human Approval و Source Provenance طراحی شود.

این سند باید مرجع اصلی برنامه‌ریزی توسعه، تقسیم کار، تست، Release و تصمیم‌های معماری پروژه باشد.

---

# 2. چشم‌انداز محصول

SakhtYar یک سامانه هوشمند برای مدیریت کامل فرآیند **مشارکت در ساخت** است.

سیستم در نهایت باید بتواند یک پرونده را از مرحله ورود اطلاعات اولیه ملک و مالکین تا تحلیل شهرسازی، امکان‌سنجی، ارزش‌گذاری، تنظیم پیشنهاد مشارکت، مذاکره، قرارداد و کنترل اجرای پروژه همراهی کند.

مسیر اصلی محصول:

```text
Case
  ↓
Property
  ↓
Owners
  ↓
Map / Location
  ↓
Documents
  ↓
Persian Agent
  ↓
Urban Rules
  ↓
Feasibility
  ↓
Market Comparables
  ↓
Valuation
  ↓
Deal Structuring
  ↓
Negotiation
  ↓
Human Approval
  ↓
Contract
  ↓
Project Execution
```

---

# 3. اصول معماری

## 3.1 Modular Monolith First

SakhtYar از ابتدا به صورت Modular Monolith توسعه داده می‌شود.

هدف این تصمیم:

- حفظ سادگی Deploy
- کاهش هزینه عملیاتی
- جلوگیری از پیچیدگی زودهنگام Microservice
- امکان توسعه مستقل Domainها
- امکان جداسازی Runtimeهای سنگین بدون بازنویسی Business Logic

Backend به صورت Maven Multi-Module سازمان‌دهی می‌شود.

ساختار هدف:

```text
backend/
├── app/
└── modules/
    ├── shared-kernel/
    ├── audit/
    ├── identity/
    ├── casefile/
    ├── property/
    ├── owner/
    ├── document/
    ├── geo/
    ├── integration-neshan/
    ├── persian-agent/
    ├── glossary/
    ├── orchestration/
    ├── urban-planning/
    ├── feasibility/
    ├── market/
    ├── valuation/
    ├── deal/
    ├── negotiation/
    ├── legal/
    ├── project-control/
    └── integrations/
```

## 3.2 جداسازی Runtimeها

یک Codebase جاوا داریم اما سیستم می‌تواند با چند Runtime مستقل اجرا شود:

```text
API
Worker
Collector
Agent
```

### API

مسئول:

- REST API
- Authentication
- UI requests
- CRUD
- Queryهای سریع

### Worker

مسئول:

- Jobهای Async
- پردازش فایل
- محاسبات سنگین
- Export/Report
- Batch Processing

### Collector

مسئول:

- دریافت داده از منابع خارجی
- جمع‌آوری اطلاعات بازار
- Sync با Providerها
- Data Normalization

### Agent

مسئول:

- Persian Agent
- Orchestrator
- Domain Agents
- LLM interaction
- Tool execution

## 3.3 External Provider Adapter

هیچ Domain اصلی نباید مستقیماً به Provider خارجی وابسته باشد.

نمونه:

```text
Geo Domain
    ↑
GeoProvider
    ↑
Neshan Adapter
```

در آینده:

```text
GeoProvider
├── Neshan
├── Balad
├── Google Maps
└── Municipality GIS
```

همین الگو برای بازار ملک، شهرداری، پیامک، OCR، AI Provider، Email، Maps، Payment و Notification استفاده می‌شود.

## 3.4 Deterministic Core

محاسبات حساس نباید صرفاً توسط LLM انجام شوند.

موارد زیر باید در Java و با قواعد Deterministic محاسبه شوند:

- درصد سهم مالکین
- محاسبات تراکم
- زیربنای قابل ساخت
- سطح اشغال
- تعداد طبقات
- پارکینگ
- هزینه ساخت
- ارزش زمین
- ارزش فروش
- ROI
- Cash Flow
- سهم مالک و سازنده

Agentها وظیفه تحلیل، توضیح، پیشنهاد و Orchestration را دارند؛ نه جایگزینی موتور محاسبات قطعی.

## 3.5 Human Approval

هر عملیات حساس باید Approval Gate داشته باشد.

```text
Agent Proposal
      ↓
Draft
      ↓
Human Review
      ↓
Approve / Reject / Edit
      ↓
Final
```

مخصوصاً برای قرارداد، پیشنهاد مالی، سهم مشارکت، ارسال پیام رسمی، قبول پیشنهاد سازنده، تحلیل حقوقی حساس و ثبت تصمیم نهایی.

---

# 4. فناوری‌های پایه

## Backend

```text
Java 21
Spring Boot 4
Spring Security
Spring Data JPA
Flyway
Maven Multi-Module
```

## Frontend

```text
React
TypeScript
Material UI
TanStack Query
Vite
RTL
```

## Data

```text
PostgreSQL
PostGIS
pgvector
pgcrypto
```

## Infrastructure

```text
Redis
MinIO
Docker Compose
Caddy
Prometheus
Grafana
```

## AI

```text
Provider abstraction
Ollama / Local model
External AI Provider
JSON contracts
pgvector
```

---

# 5. وضعیت فازها

| فاز | عنوان | وضعیت |
|---|---|---|
| Phase 0 | Foundation | ✅ تکمیل |
| Phase 1 | Property & Owners | ✅ تکمیل |
| Phase 2 | Map & Geospatial | ✅ تکمیل |
| Phase 3 | Persian Agent & Orchestrator | ⏳ برنامه‌ریزی |
| Phase 4 | Urban Planning & Feasibility | ⏳ برنامه‌ریزی |
| Phase 5 | Market Intelligence & Valuation | ⏳ برنامه‌ریزی |
| Phase 6 | Deal Structuring & Negotiation | ⏳ برنامه‌ریزی |
| Phase 7 | Legal Contract & Project Execution | ⏳ برنامه‌ریزی |
| Phase 8 | Automation & Production Hardening | ⏳ برنامه‌ریزی |

---

# Phase 0 — Foundation

## 6. هدف

ایجاد پایه‌ای که تمام فازهای بعدی بتوانند بدون بازنویسی روی آن ساخته شوند.

Phase 0 نباید Feature-heavy باشد؛ تمرکز آن روی Infrastructure، Security، Storage و Case Workspace است.

## 6.1 Scope

### Identity

- User model
- Role
- Active/Inactive
- Bootstrap Admin

### Security

- Login
- JWT
- HttpOnly Cookie
- Stateless Security
- CORS

### Case

- ساخت پرونده
- نمایش پرونده
- ویرایش پرونده
- وضعیت پرونده

### Document

- Upload
- Download
- Metadata
- SHA-256
- MinIO Object Storage

### Audit

- Actor
- Action
- Aggregate
- Payload
- Timestamp

## 6.2 Database

جداول اصلی:

```text
app_user
construction_case
case_document
audit_event
```

Extensionها:

```text
pgcrypto
postgis
vector
```

## 6.3 Infrastructure

```text
PostgreSQL
Redis
MinIO
Docker Compose
```

## 6.4 Frontend

اولین RTL Shell شامل:

- Login
- Navigation
- Cases List
- Case Create
- Case Detail
- Documents

## 6.5 معیار Done

Phase 0 زمانی کامل است که:

```text
Login
  ↓
Create Case
  ↓
Open Case
  ↓
Upload Document
  ↓
Download Document
```

بدون خطا کار کند.

همچنین Migrationها اجرا شوند، داده بعد از Restart باقی بماند، MinIO سالم باشد، Audit ثبت شود و Build Backend و Frontend موفق باشد.

**وضعیت:** ✅ تکمیل

---

# Phase 1 — Property & Owners

## 7. هدف

تبدیل Case ساده به یک پرونده واقعی مشارکت در ساخت با مدل ساخت‌یافته ملک و مالکین.

## 7.1 Property Model

هر Case یک Property اصلی دارد.

اطلاعات:

```text
province
city
district
neighborhood
address
landAreaM2
registryMainNo
registrySubNo
registrySection
postalCode
latitude
longitude
```

## 7.2 Owners

هر Property می‌تواند چند مالک داشته باشد.

اطلاعات:

```text
firstName
lastName
nationalId
mobile
ownershipNumerator
ownershipDenominator
primaryContact
```

نمونه:

```text
مالک اول    3/6
مالک دوم    2/6
مالک سوم    1/6
```

## 7.3 Business Rules

سیستم باید:

- سهم منفی قبول نکند.
- صورت از مخرج بیشتر نباشد.
- مجموع سهم بیشتر از 100٪ نشود.
- یک Primary Contact داشته باشد.
- کد ملی تکراری برای همان Property را کنترل کند.

## 7.4 API

```text
GET    /api/v1/cases/{caseId}/property
PUT    /api/v1/cases/{caseId}/property
GET    /api/v1/cases/{caseId}/owners
POST   /api/v1/cases/{caseId}/owners
PUT    /api/v1/cases/{caseId}/owners/{ownerId}
DELETE /api/v1/cases/{caseId}/owners/{ownerId}
```

## 7.5 UI

Case Workspace به Tab تبدیل می‌شود:

```text
خلاصه
مشخصات ملک
مالکین
نقشه
مدارک
```

Overview شامل مساحت، تعداد مالکین، درصد سهم ثبت‌شده، تعداد مدارک و وضعیت تکمیل پرونده است.

## 7.6 معیار Done

```text
ثبت ملک
  ↓
Refresh
  ↓
داده حفظ شود
  ↓
ثبت چند مالک
  ↓
کنترل مجموع سهم
  ↓
ویرایش / حذف
  ↓
Primary Contact
```

**وضعیت:** ✅ تکمیل

---

# Phase 2 — Map & Geospatial

## 8. هدف

اضافه کردن موقعیت مکانی واقعی ملک و ایجاد پایه Spatial برای تحلیل شهرسازی و Market Intelligence.

## 8.1 Provider

Provider اولیه:

```text
Neshan
```

اما Domain مستقل است:

```text
GeoProvider
    ↑
NeshanGeoProvider
```

## 8.2 Web Map

Frontend شامل:

- نمایش نقشه
- Marker
- Click روی نقشه
- Drag Marker
- Current Location
- Zoom
- انتخاب نقطه

Web Map Key فقط در Frontend قرار می‌گیرد.

## 8.3 Backend Geo Service

Service API Key فقط در Backend نگهداری می‌شود.

قابلیت‌ها:

```text
Address → Coordinate
Coordinate → Address
```

## 8.4 Geocoding Flow

```text
Address Input
    ↓
Neshan Geocoding
    ↓
Latitude / Longitude
    ↓
Map Center
    ↓
Marker
```

## 8.5 Reverse Geocoding

```text
Map Click
    ↓
Lat/Lng
    ↓
Reverse Geocode
    ↓
Province
City
District
Neighborhood
Address
```

## 8.6 PostGIS

علاوه بر Latitude/Longitude:

```text
location geography(Point, 4326)
```

ذخیره می‌شود.

Spatial Index:

```text
GiST
```

## 8.7 Spatial Capabilities

پایه برای Queryهای آینده:

```text
ملک‌های شعاع 500 متری
Comparableهای نزدیک
فاصله تا مترو
فاصله تا خیابان اصلی
پروژه‌های اطراف
تحلیل محله
```

## 8.8 معیار Done

- Map load شود.
- Address Geocoding کار کند.
- Reverse Geocoding کار کند.
- Marker قابل انتخاب باشد.
- مختصات ذخیره شود.
- Refresh مختصات را حفظ کند.
- PostGIS Point با Longitude/Latitude هماهنگ باشد.

**وضعیت:** ✅ تکمیل

---

# Phase 3 — Persian Agent, Glossary & Orchestrator

## 9. هدف

ایجاد لایه هوشمند فارسی که تمام تعاملات طبیعی کاربر را به درخواست‌های ساخت‌یافته و قابل اجرا تبدیل کند.

این Agent نباید جایگزین Domain Logic باشد؛ بلکه Gateway هوشمند بین کاربر و Domainهای سیستم است.

## 9.1 Persian Agent

وظایف:

- درک فارسی محاوره‌ای
- Normalize متن
- تشخیص Intent
- استخراج Entity
- رفع ابهام
- تبدیل اصطلاحات محلی
- ساخت JSON استاندارد

نمونه:

```text
این زمین 250 متره
برش 12 متره
منطقه 5 تهرانه
ببین چند طبقه میشه ساخت
```

خروجی:

```json
{
  "intent": "FEASIBILITY_ANALYSIS",
  "property": {
    "landAreaM2": 250,
    "frontageM": 12,
    "city": "تهران",
    "district": "5"
  }
}
```

## 9.2 Glossary

یک Dictionary اختصاصی برای اصطلاحات تخصصی و محلی ایجاد می‌شود.

نمونه:

```text
بر
بَر ملک
اصلاحی
عقب‌نشینی
پخ
قدرالسهم
بلاعوض
تجمیع
تراکم
سطح اشغال
عرصه
اعیان
```

## 9.3 Unknown Term Flow

اگر Persian Agent اصطلاحی را نفهمد:

```text
Unknown Term
    ↓
Clarification Request
    ↓
User Answer
    ↓
Glossary Entry Draft
    ↓
Human Confirm
    ↓
Glossary
```

Agent نباید معنی یک اصطلاح مهم را حدس بزند.

## 9.4 JSON Contracts

ارتباط Agentها باید با Schema نسخه‌بندی‌شده باشد.

```json
{
  "schemaVersion": "1.0",
  "intent": "VALUATION",
  "caseId": "...",
  "inputs": {},
  "assumptions": [],
  "missingFields": []
}
```

## 9.5 Orchestrator

Orchestrator تصمیم می‌گیرد درخواست به کدام Agent یا Domain ارسال شود.

```text
Persian Agent
     ↓
Orchestrator
     ↓
+--------------+--------------+
|              |              |
Property   Urban Agent   Valuation Agent
```

## 9.6 Agent Registry

برای هر Agent این مشخصات ثبت می‌شود:

```text
name
version
capabilities
inputSchema
outputSchema
requiredPermissions
timeout
provider
```

## 9.7 Conversation Context

Context باید Case-aware باشد. Agent باید بداند کاربر در حال کار روی کدام Case/Property است بدون اینکه داده غیرمرتبط به مدل ارسال شود.

## 9.8 AI Provider Abstraction

Domain نباید به یک LLM خاص وابسته باشد.

```text
AiProvider
├── Local/Ollama
├── OpenAI-compatible
└── Future Provider
```

## 9.9 Audit

برای هر Agent run ثبت می‌شود:

```text
Agent
Version
Input
Output
Tools
Duration
Status
Human Approval
```

## 9.10 UI

یک Assistant Panel در Case Workspace اضافه می‌شود.

امکانات:

- Chat
- پیشنهاد سوال
- نمایش Extracted Fields
- Clarification
- Agent Result
- Apply to Case
- Reject

## 9.11 معیار Done

کاربر بتواند:

```text
متن فارسی
  ↓
Intent
  ↓
JSON
  ↓
Agent Routing
  ↓
Structured Result
```

را انجام دهد و Unknown Term به Clarification منجر شود.

---

# Phase 4 — Urban Planning & Feasibility

## 10. هدف

ایجاد موتور تحلیل ضوابط شهرسازی و امکان‌سنجی ساخت.

## 10.1 Urban Planning Module

اطلاعات مورد نیاز:

```text
Zone
District
Parcel
Land Area
Frontage
Passage Width
Density
Coverage
Floors
Setback
Parking
Correction
Land Use
```

## 10.2 Urban Rule Source

هر Rule باید Source داشته باشد:

```text
sourceType
sourceName
sourceUrl
documentId
effectiveDate
retrievedAt
ruleVersion
```

هدف این است که سیستم بتواند توضیح دهد این نتیجه بر اساس کدام ضابطه به دست آمده است.

## 10.3 Municipality Adapter

```text
UrbanRuleProvider
├── Municipality API
├── GIS Provider
├── Document Import
└── Manual Verified Rule
```

اگر API رسمی در دسترس نباشد، داده باید از مسیر کنترل‌شده و قابل Audit وارد شود.

## 10.4 Feasibility Engine

محاسبات Deterministic:

```text
Buildable Footprint
Gross Floor Area
Saleable Area
Common Area
Parking
Floor Count
Unit Count
Setbacks
```

## 10.5 Scenario Engine

سیستم باید چند سناریو بسازد:

```text
Conservative
Base
Optimistic
```

اما تفاوت سناریوها باید بر اساس Assumptionهای شفاف باشد.

## 10.6 Risk

Risk Agent مواردی مثل کمبود پارکینگ، عرض گذر، اصلاحی، محدودیت ارتفاع، ابهام ضابطه، مغایرت داده و نیاز به استعلام را Flag می‌کند.

## 10.7 UI

Tab جدید:

```text
تحلیل شهرسازی
```

شامل:

- Inputs
- Source
- Rules
- Calculation
- Scenario
- Risk
- Assumptions
- Missing Data

## 10.8 معیار Done

سیستم برای یک Property بتواند:

```text
Urban Rules
  ↓
Feasibility Calculation
  ↓
Scenario
  ↓
Risks
```

تولید کند و تمام Inputs و Sources را نمایش دهد.

---

# Phase 5 — Market Intelligence & Valuation

## 11. هدف

تحلیل بازار واقعی، جمع‌آوری Comparable و محاسبه ارزش ملک و پروژه.

## 11.1 Market Collector

Collectorها مستقل از Domain هستند.

```text
MarketSourceAdapter
├── Source A
├── Source B
├── Internal Dataset
└── Manual Comparable
```

منبعی مثل Divar نباید با API غیررسمی Hard-code شود. هر Source باید Adapter مستقل داشته باشد.

## 11.2 Raw Data

داده خام باید جدا ذخیره شود:

```text
source
externalId
rawPayload
retrievedAt
hash
```

بعد Normalization انجام شود.

## 11.3 Normalized Listing

```text
type
location
area
age
floor
parking
elevator
price
pricePerM2
coordinates
sourceDate
```

## 11.4 Comparable Engine

Comparable بر اساس Distance، Neighborhood، Area، Age، Building Type، Amenities و Date انتخاب می‌شود.

## 11.5 Outlier Detection

داده‌های غیرواقعی باید Flag شوند:

```text
price anomaly
duplicate
stale
wrong location
extreme area
```

## 11.6 Valuation

Valuation Engine:

```text
Land Value
Current Property Value
Future Sale Value
Construction Cost
Indirect Cost
Permit Cost
Finance Cost
Risk Margin
```

## 11.7 Construction Cost

هزینه ساخت به صورت Versioned Dataset نگهداری می‌شود:

```text
structure
finishLevel
location
date
costPerM2
source
```

## 11.8 UI

Tab:

```text
بازار و ارزش‌گذاری
```

شامل Comparable Map، Comparable Table، Price per m²، Trend، Adjustments، Final Estimate، Confidence و Source.

## 11.9 معیار Done

```text
Nearby Listings
  ↓
Normalize
  ↓
Filter
  ↓
Comparable
  ↓
Valuation
```

با Source و تاریخ مشخص انجام شود.

---

# Phase 6 — Deal Structuring & Negotiation

## 12. هدف

تبدیل تحلیل مالی و فنی به پیشنهادهای قابل مذاکره برای مالک و سازنده.

## 12.1 Deal Model

```text
Owner Share
Builder Share
Cash Payment
Goodwill / BilaAvaz
Delivery Terms
Unit Allocation
Parking Allocation
Timeline
Guarantee
```

## 12.2 Deal Scenario

```text
Scenario A
مالک 55
سازنده 45

Scenario B
مالک 60
سازنده 40
بلاعوض

Scenario C
مالک 57
سازنده 43
```

## 12.3 Financial Analysis

برای هر Scenario:

```text
Builder Cost
Builder Revenue
Builder ROI
Owner Value
Owner Uplift
Break-even
Cash Requirement
Sensitivity
```

محاسبه می‌شود.

## 12.4 Sensitivity Analysis

سیستم اثر تغییر قیمت فروش، هزینه ساخت، زمان اجرا، نرخ تورم، سهم و بلاعوض را بررسی می‌کند.

## 12.5 Negotiation

Negotiation Timeline:

```text
Offer
Counter Offer
Comment
Revision
Meeting
Agreement
Rejected
Approved
```

## 12.6 Negotiation Agent

Agent می‌تواند اختلاف طرفین را خلاصه کند، Scenario جایگزین پیشنهاد دهد، اثر مالی Counter Offer را محاسبه کند و نکات مذاکره را آماده کند. Approval نهایی انسانی است.

## 12.7 Proposal Versioning

هر Proposal نسخه دارد:

```text
v1
v2
v3
```

هیچ پیشنهاد قبلی Overwrite نمی‌شود.

## 12.8 معیار Done

برای یک Case چند پیشنهاد ساخته شود و بتوان مسیر Compare → Negotiate → Revise → Approve را Audit کرد.

---

# Phase 7 — Legal, Contract & Project Execution

## 13. هدف

تبدیل Deal تاییدشده به قرارداد و سپس مدیریت اجرای پروژه.

## 13.1 Contract Module

```text
Parties
Property
Deal
Clauses
Milestones
Guarantees
Penalties
Delivery
Termination
Attachments
```

## 13.2 Clause Library

Clauseها Versioned هستند:

```text
clauseCode
title
text
category
version
legalReviewStatus
```

## 13.3 Legal Agent

کار Legal Agent:

- یافتن Clauseهای مرتبط
- بررسی Missing Clause
- Risk Flag
- Compare Version
- Summary

کار Legal Agent نیست:

- صدور نظر حقوقی قطعی بدون Review
- تایید نهایی قرارداد

## 13.4 Contract Approval

```text
Draft
  ↓
Legal Review
  ↓
Owner Review
  ↓
Builder Review
  ↓
Approved
```

## 13.5 Execution Project

بعد از Contract، Construction Project ایجاد می‌شود.

## 13.6 Project Milestones

نمونه:

```text
تخریب
گودبرداری
فونداسیون
اسکلت
سفت‌کاری
تاسیسات
نازک‌کاری
نما
تحویل
```

## 13.7 Progress

برای هر Milestone:

```text
plannedStart
plannedEnd
actualStart
actualEnd
progressPercent
status
cost
documents
photos
```

## 13.8 Cost Control

```text
Budget
Committed
Paid
Remaining
Variance
```

## 13.9 Change Order

هر تغییر باید ثبت شود:

```text
reason
scope
costImpact
timeImpact
approval
```

## 13.10 UI

Tabهای جدید:

```text
قرارداد
اجرا
زمان‌بندی
هزینه
گزارش
```

## 13.11 معیار Done

یک Deal تایید شده بتواند مسیر Contract → Approval → Execution Project → Milestones → Progress → Cost را طی کند.

---

# Phase 8 — Automation, Agents & Production Hardening

## 14. هدف

تبدیل سیستم از MVP کامل به Platform قابل بهره‌برداری Production.

## 14.1 Agent Automation

Agentها:

```text
Persian Agent
Intake Agent
Document Agent
Urban Planning Agent
Feasibility Agent
Market Intelligence Agent
Valuation Agent
Deal Structuring Agent
Matching Agent
Negotiation Agent
Legal Agent
Risk Agent
Project Control Agent
```

## 14.2 Orchestration

Workflowهای کامل:

```text
User Request
  ↓
Persian Agent
  ↓
Orchestrator
  ↓
Domain Agents
  ↓
Deterministic Services
  ↓
Human Approval
  ↓
Action
```

## 14.3 Async Job System

Jobها برای Data Collection، AI Tasks، OCR، Large Document Processing، Reports، Market Sync و Notifications به Worker منتقل می‌شوند.

## 14.4 Observability

Production باید شامل:

```text
Prometheus
Grafana
Structured Logs
Health Checks
Metrics
Alerting
```

باشد.

## 14.5 Security Hardening

- HTTPS
- Secure Cookie
- CSRF strategy
- Rate Limiting
- RBAC
- Secret Management
- Brute-force protection
- Audit Review
- Upload Validation
- Malware scan path
- Dependency scanning

## 14.6 Backup

حداقل:

```text
Nightly PostgreSQL Backup
MinIO Backup
Off-server Copy
Restore Test
```

Restore Test مهم‌تر از صرفاً Backup گرفتن است.

## 14.7 Production Deployment

Topology پایه:

```text
Caddy
Frontend
API
Worker
Collector
Agent
PostgreSQL
Redis
MinIO
```

## 14.8 Scaling

مرحله اول:

```text
1 Server
```

مرحله دوم:

```text
Server 1: API + Frontend
Server 2: Worker + Collector + Agent
Server 3: PostgreSQL
```

بدون تغییر Business Domain.

## 14.9 AI Evaluation

برای Agentها Evaluation Dataset ساخته می‌شود.

```text
Persian Intent Accuracy
Entity Extraction
Glossary Resolution
Urban Explanation
Valuation Explanation
Tool Selection
Hallucination Rate
```

## 14.10 Cost Control

برای AI ثبت می‌شود:

```text
provider
model
tokens
latency
cost
agent
case
```

تا استفاده Agentها قابل کنترل باشد.

## 14.11 معیار Done

Production زمانی آماده است که Backup و Restore تست شده باشد، Monitoring فعال باشد، Agentها Traceable باشند، Secrets امن باشند، Deployment قابل تکرار باشد، Failure یک Worker باعث Down شدن API نشود، Audit کامل باشد و Integration Testها سبز باشند.

---

# 15. وابستگی فازها

```text
Phase 0
  ↓
Phase 1
  ↓
Phase 2
  ↓
Phase 3
  ↓
Phase 4
  ↓
Phase 5
  ↓
Phase 6
  ↓
Phase 7
  ↓
Phase 8
```

بعضی فعالیت‌ها می‌توانند Parallel باشند. به‌عنوان مثال Phase 6 به خروجی Phase 4 و Phase 5 وابسته است و بدون Feasibility و Valuation قابل اعتماد نیست.

---

# 16. تکامل Moduleها

## تا Phase 2

```text
shared-kernel
audit
identity
casefile
property
owner
document
geo
integration-neshan
```

## Phase 3

```text
persian-agent
glossary
orchestration
ai-provider
```

## Phase 4

```text
urban-planning
feasibility
risk
```

## Phase 5

```text
market
collector
valuation
```

## Phase 6

```text
deal
negotiation
financial-analysis
```

## Phase 7

```text
legal
contract
project-control
```

---

# 17. اصول Data Provenance

برای داده‌های حساس باید مشخص باشد:

```text
چه کسی؟
از کجا؟
چه زمانی؟
با چه نسخه‌ای؟
```

نمونه Urban Rule:

```text
Source: Municipality Rule
Effective Date: ...
Retrieved At: ...
Provider: ...
Document Hash: ...
```

نمونه Market:

```text
Source
Listing ID
Listing Date
Collected At
Raw Payload Hash
```

نمونه AI:

```text
Agent Version
Prompt Version
Model
Input
Output
```

---

# 18. Versioning

موارد زیر باید Version داشته باشند:

- DB Migration
- API
- JSON Contract
- Agent
- Prompt
- Glossary Entry
- Urban Rule
- Valuation Model
- Proposal
- Contract
- Clause

---

# 19. Testing Strategy

## Unit Test

برای Business Rules، Calculations، Ownership، Feasibility، Valuation و Deal Math.

## Integration Test

برای PostgreSQL، PostGIS، MinIO، Redis و Provider Adapter.

## API Test

برای Endpointها.

## UI Smoke Test

برای مسیرهای اصلی.

## Agent Evaluation

برای AI.

---

# 20. Definition of Done عمومی

یک Phase فقط زمانی Done است که:

1. Backend Build موفق باشد.
2. Frontend Build موفق باشد.
3. Migration سالم اجرا شود.
4. Smoke Test پاس شود.
5. داده بعد از Refresh باقی بماند.
6. Audit در عملیات حساس ثبت شود.
7. Documentation به‌روزرسانی شده باشد.
8. Error مهم در Console/Log وجود نداشته باشد.
9. Regression فازهای قبلی پاس شود.
10. Git Commit تمیز ایجاد شود.

---

# 21. استراتژی Git

Branch اصلی:

```text
main
```

برای هر فاز:

```text
Implement
  ↓
Build
  ↓
Smoke Test
  ↓
Commit
```

نمونه Commit:

```text
feat: complete phase 3 persian agent foundation
```

---

# 22. استراتژی Release

نسخه پیشنهادی:

```text
0.1.x   Phase 0
0.2.x   Phase 1-2
0.3.x   Phase 3
0.4.x   Phase 4
0.5.x   Phase 5
0.6.x   Phase 6
0.7.x   Phase 7
1.0.0   Production-ready Phase 8
```

این شماره‌گذاری Guideline است و در صورت نیاز قابل تغییر است.

---

# 23. Roadmap اجرایی از وضعیت فعلی

وضعیت فعلی:

```text
Phase 0 ✅
Phase 1 ✅
Phase 2 ✅
```

قدم بعدی:

```text
Phase 3
Persian Agent
  ↓
Glossary
  ↓
JSON Contract
  ↓
Orchestrator
  ↓
Agent Audit
  ↓
Assistant UI
```

بعد:

```text
Phase 4
Urban Planning
+
Feasibility
```

و سپس:

```text
Phase 5
Market
+
Valuation
```

---

# 24. هدف نهایی

در نسخه نهایی، فرآیند ایده‌آل کاربر باید شبیه این باشد:

```text
ثبت ملک
  ↓
ثبت مالکین
  ↓
انتخاب روی نقشه
  ↓
آپلود اسناد
  ↓
تکمیل داده با Persian Agent
  ↓
دریافت ضوابط
  ↓
امکان‌سنجی
  ↓
تحلیل بازار
  ↓
ارزش‌گذاری
  ↓
ساخت پیشنهاد مشارکت
  ↓
مذاکره
  ↓
تایید انسانی
  ↓
قرارداد
  ↓
کنترل اجرا
```

SakhtYar در پایان باید بتواند تمام این مسیر را در یک **Case Workspace واحد، Audit-able، Source-aware و Agent-assisted** مدیریت کند.

---

# 25. اصل راهبردی پروژه

> **AI پیشنهاد و تحلیل می‌کند، Domain Logic محاسبه می‌کند، Source شواهد را تامین می‌کند و انسان تصمیم نهایی را می‌گیرد.**

این اصل باید در تمام فازهای SakhtYar حفظ شود.
