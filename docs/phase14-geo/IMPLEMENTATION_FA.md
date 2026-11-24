# فاز ۱۴ | ماژول Geo / GIS — گزارش پیاده‌سازی

## مبنای سورس و محدوده
- مخزن: `HAminzadeh/SakhtYar`، شاخه `v2`
- HEAD مبنا: `698280f3683979691f4e1a72fb7ab514186162ed` (2026-11-24 13:31:19 UTC)
- ماژول مالک: `backend/modules/geo`؛ آداپتور وابسته: `backend/modules/integration-neshan`
- مصرف‌کننده فعلی: `frontend/src/features/case/PropertyMap.tsx` و `PropertyPanel.tsx`
- وضعیت: **READY_TO_VERIFY** تا اجرای Maven و PostgreSQL clone و UI E2E.

## ۱) ممیزی بر مبنای روش و فایل واقعی

| مسیر / قابلیت | پیش از فاز ۱۴ | نقص اثبات‌شده | تصمیم | آزمون |
|---|---|---|---|---|
| `GeoSpatialRepository.findNearbyProperties` | query محدود به radius و ۵۰ رکورد | فاقد ACL و فیلتر `REAL`؛ افشای مختصات، عنوان و آدرس پرونده دیگران | اصلاح SQL و auth boundary، بدون تغییر DTO | GeoSpatialSqlContractTest + clone permission tests |
| `GeoService.reverseGeocode` | call مستقیم Provider | فاقد validation مختصات در مرز سرویس | اعتبارسنجی WGS84 قبل از call | GeoServiceTest |
| `GeoService.geocode` | فقط blank check | محدودیت طول و کنترل جواب provider ندارد | کنترل ورودی و خروجی | GeoInputPolicyTest, GeoServiceTest |
| `GeoService.providerAvailable` | فقط موجود بودن Bean | false positive برای نبود کلید نشان | `GeoProvider.configured()`؛ نشان فقط با کلید حاضر ready است | GeoServiceTest, NeshanEgressPolicyTest |
| `GeoController` | status/geocode/search/reverse/nearby | ورودی address بدون سقف مشخص | `@NotBlank/@Size` و status CRS افزایشی | GeoControllerHttpTest |
| `NeshanGeoProvider` | TESTER guard پیش از HTTP وجود دارد | مختصات نامعتبر upstream ممکن است به UI برسد؛ provider مستقیم نیز نیازمند validation است | validation ورودی/خروجی، حفظ TESTER guard | NeshanMockHttpTest, NeshanEgressPolicyTest |
| `NeshanConfiguration` | RestClient بدون timeout اختصاصی | timeoutSeconds تنظیمات استفاده نشده | connect/read timeout با سقف ۳۰ ثانیه | تست ساخت و Mock HTTP |
| `V4__property_postgis_location.sql` | `geography(Point,4326)` + Trigger + GiST | هیچ نقص اثبات‌شده‌ای برای نیاز به Migration جدید وجود ندارد | **عدم ایجاد Migration**؛ آزمون clone | `POSTGIS_CLONE_READONLY.sql` |
| `PropertyMap.tsx` | انتخاب و ذخیره مختصات از طریق Property | UI برای این تغییرات به قرارداد API جدید نیاز ندارد | reuse؛ عدم تغییر غیرضروری | `npm run build` و browser E2E |
| `PropertyNumeric.validate` | جفت مختصات، محدوده و precision را برای persist کنترل می‌کند | قرارداد ذخیره معتبر است | reuse بدون duplication | تست‌های Property موجود |

## ۲) گراف وابستگی

```text
GeoController (GET API)
  └─ GeoService (WGS84 policy + CASE_READ / !TESTER)
      ├─ GeoProvider (configured + geocode + reverse)
      │   └─ NeshanGeoProvider (offline mocked tests / optional real provider)
      └─ GeoSpatialRepository (SQL-side REAL + case ACL)
          ├─ public.property.location :: geography(Point,4326)
          ├─ public.construction_case
          └─ public.construction_case_access
Frontend PropertyMap --(GET /api/v1/geo/**)--> GeoController
Frontend PropertyMap --(PUT /api/v1/cases/{id}/property)--> Property module
```

## ۳) تهدیدها و کنترل‌ها

- **IDOR / location leakage:** SQL با `REAL`, workspace NULL و یکی از `ADMIN` یا سازنده پرونده یا عضویت فعال پرونده نتیجه را محدود می‌کند. این فیلتر قبل از materialization اجرا می‌شود. نام کاربری از Spring Security گرفته می‌شود و parameter کنترل‌پذیر کلاینت نیست.
- **TEST data cross-scope:** `c.data_scope='REAL'`, `p.data_scope='REAL'`, `m.data_scope='REAL'` همراه با workspace NULL. همچنین دسترسی TESTER در Service و Repository جداگانه رد می‌شود. این کنترل جایگزین اثبات DB role و RLS در PostgreSQL واقعی نیست.
- **Geocode external disclosure:** آدرس ارسالی به نشان فقط پس از درخواست صریح کاربر و داشتن `CASE_READ` ارسال می‌شود. نقش `TESTER` هرگز به سرویس بیرونی وصل نمی‌شود. آزمون‌ها فقط از MockRestServiceServer استفاده می‌کنند.
- **Malformed coordinates:** ورودی WGS84 و حداکثر precision درخواست و خروجی Provider کنترل می‌شود؛ UI مختصات نامعتبر دریافت نمی‌کند.
- **DoS:** سقف ۲۰۰۰۰ متر radius، سقف ۵۰ نتیجه، استفاده از `ST_DWithin` به همراه GiST index موجود، timeout اتصال/خواندن نشان، طول آدرس حداکثر ۳۰۰ کاراکتر.
- **Credential exposure:** کلید نشان در Response/Status/Log منتشر نمی‌شود. فیلد `providerAvailable` فقط bool است و تضمین معتبر بودن کلید نزد سرور خارجی نیست.
- **Read-only:** هیچ سرویس جدیدی برای تغییر Geometry یا پرونده ایجاد نشده است. ذخیره‌سازی همچنان در Property انجام می‌شود و مجوزهای آن مستقل است.
- **Disclaimer:** نقشه و Geocoding جایگزین اطلاعات ثبتی و تعیین حدود رسمی ملک نیستند.

## ۴) سازگاری API

| Route | رفتار | تغییر |
|---|---|---|
| GET `/api/v1/geo/status` | readiness بدون تماس با Neshan | فیلد `coordinateReferenceSystem` افزایشی؛ `providerAvailable` فقط با config معتبر true |
| GET `/api/v1/geo/geocode?address=...` | lookup آدرس | validation ورودی/خروجی، پاسخ 400 یا 502 برای invalid |
| GET `/api/v1/geo/search?term=...` | alias قدیمی | حفظ route و semantics؛ lat/lng اختیاری بدون استفاده |
| GET `/api/v1/geo/reverse?lat=&lng=` | reverse lookup | validate before egress |
| GET `/api/v1/geo/nearby-properties?lat=&lng=&radiusMeters=` | فاصله تا املاک | **اصلاح عمدی امنیت:** اکنون فقط املاک مجاز کاربر |

پاسخ `NearbyProperty` از نظر فیلدها تغییر نکرده؛ تعداد نتیجه برای کاربران عادی ممکن است کاهش پیدا کند، چون دسترسی غیرمجاز قبلاً نادیده گرفته می‌شد.

## ۵) GIS / SRID / فرمول

- همه ورودی‌های HTTP مختصات decimal درجه WGS84 هستند: `lat` از -90 تا 90؛ `lng` از -180 تا 180.
- در SQL ترتیب پارامترهای PostGIS عمداً `ST_MakePoint(:lng, :lat)` است؛ x طول جغرافیایی و y عرض جغرافیایی.
- `ST_SetSRID(...,4326)::geography` محاسبه فاصله را به **متر** تبدیل می‌کند؛ `ST_DWithin` با GiST index `idx_property_location_gist` طراحی شده است.
- مسیر persistence همچنان از Trigger موجود `trg_property_sync_geography` استفاده می‌کند؛ Schema مهاجرت V4 تغییر نمی‌کند.
- تساوی فاصله با `p.case_id ASC` قطعی است.
- `EXPLAIN` روی کلون ایزوله برای بررسی انتخاب واقعی Index الزامی است؛ طرح واقعی به حجم و statistics بستگی دارد.

## ۶) ایمنی و اجرای Migration

**فاز ۱۴ Migration جدید ندارد.** فایل‌های Flyway قبلی (V4، V58، V60، V67 و V68) تغییر داده نشده‌اند. نصب‌کننده به PostgreSQL و Flyway متصل نمی‌شود.

اخطار محیط: در پروفایل `local`، Flyway از فاز ۱۳ غیرفعال شده، اما `ddl-auto: validate` ممکن است در فقدان جداول مهاجرت‌های تأییدنشده مانع Startup شود. پروفایل `server` و هر override دیگر باید پیش از اتصال به دیتابیس واقعی بررسی شود.

فایل `POSTGIS_CLONE_READONLY.sql` برای DBA است: فقط روی کلون و با یوزر محدود، در تراکنش READ ONLY اجرا شود. برای اثبات end-to-end ACL/RLS دست‌کم این سناریوها جداگانه لازم‌اند:
1. دو پرونده REAL از مالک‌های متفاوت؛ یک مورد در radius، یک مورد بیرون radius، کاربر غیرعضو نباید ملک دیگری را ببیند.
2. membership فعال/غیرفعال؛ تأثیر آن بر Nearby بررسی شود.
3. TEST scope و workspace ایزوله؛ هیچ ردیف TEST برای REAL endpoint نمایش داده نشود.
4. بررسی index و round-trip geometry از property coordinate pair و Trigger روی داده مصنوعی **صرفاً در کلون**.
5. دسترسی در نقش PostgreSQL محدود و بدون `BYPASSRLS` و با `FORCE ROW LEVEL SECURITY` به‌صورت واقعی آزمایش شود.

## ۷) اجرا و تست محلی

```powershell
# from D:\ChatGPT_Projects\SakhtYar\source\backend
mvn -o -pl modules/integration-neshan -am clean test `
  '-Dtest=Phase14GeoInputPolicyTest,Phase14GeoServiceTest,Phase14GeoSpatialSqlContractTest,Phase14GeoControllerHttpTest,Phase14NeshanEgressPolicyTest,Phase14NeshanMockHttpTest,NeshanTesterBoundaryTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false'

# from frontend
npm run build
```

Verification: حتماً وضعیت هر گزارش Surefire، تعداد تست، errors/failures/skips و exit code بررسی شود. اجرای واقعی Maven/Node/PostGIS در این محیط به‌شکل کامل ممکن نبوده و نباید PASSED اعلام شود.

## ۸) فهرست آزمون‌ها

| Test class | Scope |
|---|---|
| Phase14GeoInputPolicyTest | boundary, invalid coordinate, precision, address control, radius |
| Phase14GeoServiceTest | auth, TESTER denial, null provider, invalid input/output, proximity delegation |
| Phase14GeoSpatialSqlContractTest | SQL shape, REAL scope, membership, ST_DWithin, no DB access for TESTER |
| Phase14GeoControllerHttpTest | stable endpoints, status, missing query parameter |
| Phase14NeshanEgressPolicyTest | empty credentials, bad coordinate, TESTER egress denied |
| Phase14NeshanMockHttpTest | in-memory HTTP geocode, malformed provider coordinate, reverse request |
| NeshanTesterBoundaryTest (existing) | original TESTER denial |

## ۹) Installer, Backup, Rollback

فایل `Apply-SakhtYar-Geo-Phase14.ps1` فایل ZIP را از Downloads خودکار استخراج می‌کند، SHA256 ZIP و همه فایل‌ها را کنترل می‌کند، HEAD، branch، origin و Git blob فایل‌های جایگزین‌شونده را می‌سنجد و در حضور تغییر محلی متوقف می‌شود. فقط فایل‌های manifest را جایگزین می‌کند و backup در کنار مخزن می‌سازد.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Downloads\Apply-SakhtYar-Geo-Phase14.ps1" -Mode SelfTest
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Downloads\Apply-SakhtYar-Geo-Phase14.ps1" -Mode Audit
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Downloads\Apply-SakhtYar-Geo-Phase14.ps1" -Mode DryRun
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Downloads\Apply-SakhtYar-Geo-Phase14.ps1" -Mode ApplySource
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Downloads\Apply-SakhtYar-Geo-Phase14.ps1" -Mode Verify
```

Rollback فقط با ارائه مسیر backup همان اجرا و بدون تغییر مجدد فایل‌های نصب‌شده انجام می‌شود؛ دیتابیس موضوع این Rollback نیست.

## ۱۰) Acceptance gates

- GitHub HEAD audit: **PASS**
- Java 21 local stand-in compilation of ۷ changed production classes: **PASS**; independent WGS84 smoke: **PASS — ۱۹ assertions**; static SQL-shape check: **PASS — ۱۰ assertions**. Stubs are only syntax/interface approximations and do **not** substitute for real Maven or PostgreSQL testing.
- Dedicated Maven + Mockito + MockRestServiceServer: **READY_TO_VERIFY**
- Frontend compile and UI E2E: **READY_TO_VERIFY**
- PostgreSQL clone geometry, GiST, RLS, negative IDOR: **BLOCKED WITHOUT ISOLATED DB**
- No external SMS/AI/crawler/Neshan calls in testing: **TEST SUITE OFFLINE BY DESIGN**
- Production readiness: **NOT VERIFIED**

## ۱۱) وابستگی فاز بعد

قبل از تکمیل ادعایی فاز ۱۵، به نتیجه واقعی تست‌ها و کنترل دیتابیس clone نیاز داریم. هر خطای Maven یا runtime باید به‌عنوان Hotfix مستقل با baseline جدید و آزمون محدود اصلاح شود.
