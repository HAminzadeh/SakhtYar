# SakhtYar Property Flexible JSONB v1

این Patch مدل Property را از یک مدل ثابت به یک مدل هیبریدی تبدیل می‌کند.

## معماری

### ستون‌های استاندارد

فیلدهایی که برای جستجو، گزارش و تحلیل ساختمانی پرتکرار هستند ستون واقعی دارند:

- landAreaM2
- frontageM
- passageWidthM
- buildingAreaM2
- constructionYear
- existingFloors
- existingUnits
- orientation
- propertyType
- buildingCondition
- province / city / district / neighborhood / address
- اطلاعات ثبتی و مختصات

### JSONB انعطاف‌پذیر

هر Fact مفید دیگری در:

`property.attributes`

ذخیره می‌شود.

مثال:

```json
{
  "location": "پیروزی",
  "parkingSpaces": 2,
  "hasElevator": false,
  "deedType": "شش دانگ",
  "roofCondition": "قابل استفاده",
  "ownerNote": "مالک برای مشارکت آماده مذاکره است"
}
```

ستون `attributes` از نوع PostgreSQL `JSONB` است و GIN index دارد.

## Human approval

Agent مستقیماً Property را تغییر نمی‌دهد.

کاربر باید دکمه:

`اعمال همه اطلاعات استخراج‌شده روی ملک`

را بزند. سپس:

`PATCH /api/v1/cases/{caseId}/property/facts`

صدا زده می‌شود.

Backend خودش تشخیص می‌دهد:
- فیلد استاندارد -> ستون
- فیلد جدید -> JSONB

## Merge behavior

اطلاعات JSONB قبلی پاک نمی‌شوند. Factهای جدید روی اطلاعات موجود Merge می‌شوند.

در PUT معمولی Property نیز اگر `attributes` ارسال نشود (`null`) اطلاعات JSONB قبلی حفظ می‌شود.

## Agent context

PropertyAgent علاوه بر ستون‌های استاندارد، تمام `attributes` را نیز وارد context می‌کند. بنابراین Agentهای بعدی می‌توانند داده‌های تکمیلی ذخیره‌شده را مصرف کنند.

## Migration

Migration جدید:

`V6__property_flexible_attributes.sql`

## اجرا

```powershell
.\apply-property-flexible-jsonb.ps1 -Repo "C:\Projects\SakhtYar"

cd C:\Projects\SakhtYar\backend
mvn clean test

cd ..\frontend
npm run build
```

سپس Backend و Frontend را Restart کنید.

## تست پیشنهادی

در Assistant بنویسید:

`یه ملک ۵۰۰ متری تو پیروزی دارم که برای سال ۱۳۵۰ هست، دو طبقه و جنوبی هست، پارکینگ هم دو تا داره و آسانسور نداره`

بعد روی «اعمال همه اطلاعات استخراج‌شده روی ملک» بزنید.

انتظار:

ستون‌ها:
- land_area_m2 = 500
- construction_year = 1350
- existing_floors = 2
- orientation = SOUTH

JSONB:
- location = پیروزی
- parkingSpaces = 2
- hasElevator = false

توجه: این Patch با Persian Extraction v3 هماهنگ طراحی شده است.
