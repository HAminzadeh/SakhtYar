# SakhtYar Phase 2 - Neshan Geocoding URI Fix

این Patch خطای زیر را اصلاح می‌کند:

`Not enough variable values available to expand '"address"'`

علت این بود که JSON شامل `{}` مستقیماً داخل `queryParam` قرار می‌گرفت و Spring آن را به‌عنوان URI Template variable تفسیر می‌کرد.

اصلاح:

```java
.uri("/geocoding/v1?json={json}", payload)
```

در این حالت `payload` به‌عنوان مقدار متغیر URI ارسال و به‌درستی encode می‌شود.

## اعمال Patch

محتویات ZIP را روی ریشه پروژه کپی و Replace کنید.

سپس:

```powershell
cd D:\ChatGPT_Projects\SakhtYar\source\backend
mvn clean install
```

Backend را Restart و دوباره جستجوی آدرس را تست کنید.
