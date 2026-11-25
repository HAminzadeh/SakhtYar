# فاز ۱۶ — ماتریس پذیرش ۵۲ مورد

برچسب‌ها: `CODE` = سورس/تست اضافه یا اصلاح شده ولی آزمون محیطی لازم؛ `REUSE` = قابلیت پایه قبلی عمداً حفظ شد؛ `BLOCKED` = وابسته به DB/Browser/شبکه کنترل‌شده/تصمیم کسب‌وکار؛ `VERIFY` = تست آماده اما هنوز روی ویندوز اجرا نشده.

| # | الزام | وضعیت | شاهد/کار باقی‌مانده |
|---|---|---|---|
| 1 | مرزهای معماری | CODE | GlobalController, Serviceها و GlobalizationPolicy |
| 2 | GlobalController | CODE | @Valid، Review/Quote و خطاهای سرویس؛ HTTP Verify |
| 3 | زبان‌ها | REUSE | CatalogService.languages، کنترل دیتابیس لازم |
| 4 | کشورها | CODE | جست‌وجوی فارسی و escape wildcard؛ DB تست |
| 5 | تقسیمات | REUSE | CatalogService.divisions؛ سنجش hierarchy روی clone |
| 6 | شهرها | CODE | کنترل جست‌وجوی ورودی، pagination قبلی؛ performance pending |
| 7 | hierarchy | CODE | JurisdictionService.validateHierarchy، active=true |
| 8 | ثبت مکان پرونده | CODE | CaseAccess/lock/scope در Service؛ DB pending |
| 9 | خواندن مکان پرونده | CODE | CaseAccess برای forCase؛ IDOR test |
| 10 | Crawler region matching | CODE | فیلتر REAL و کنترل CaseAccess |
| 11 | Knowledge/Urban regional matching | CODE | SQL scope و CaseAccess؛ DB pending |
| 12 | stampLearning/candidate | CODE | requireCase در forCase؛ caller integration pending |
| 13 | تنظیمات کاربری | CODE | merge non-null، audit، round-trip |
| 14 | اعتبارسنجی ترجیحات | CODE | timezone/date/numerals/weekday |
| 15 | همگام‌سازی UI | REUSE | PreferencesPage و LanguageProvider؛ E2E pending |
| 16 | زبان FA/EN | REUSE | i18n موجود؛ Full browser pending |
| 17 | Unicode | CODE | Normalizer و ی/ک در search؛ DB Unicode audit pending |
| 18 | RTL/LTR | REUSE | LanguageProvider/antd؛ browser pending |
| 19 | Jalali/Gregorian | CODE | Intl Persian/Gregory display؛ browser golden pending |
| 20 | timezone تهران/UTC | CODE | IANA validation + formatZonedDate؛ DST E2E pending |
| 21 | اعداد فارسی | CODE | numberFormat in UI + NumberFormat؛ parsing backend global contract pending |
| 22 | واحدهای سطح | CODE | formatSquareMeters؛ conversion/migration not needed for canonical m² |
| 23 | ارزها | REUSE | CurrencyCatalogBootstrap و seed موجود |
| 24 | TOMAN/IRR | CODE | exact BigDecimal 10x, V24 seed |
| 25 | monetary precision | CODE | BigDecimal/quote minor-unit rounding |
| 26 | FX submit | CODE | pending, validation, duplicate guard, source |
| 27 | FX review | CODE | privileged `/fx-rates/{id}/review` و Audit؛ two-person rule pending |
| 28 | FX convert | CODE | approved direct/inverse, deterministic selection |
| 29 | FX effective time | CODE | observedAt/validFrom/validTo/stale |
| 30 | FX provenance | CODE | Quote rateId/source/observedAt |
| 31 | FX unavailable | CODE | no fake FX fallback, explicit not-found |
| 32 | GeoNames malformed input | CODE | ZIP entry/dataset/size guards؛ extended decompression limits pending |
| 33 | GeoNames HTTPS | CODE | fixed allowlist, no redirects, outbound=false default |
| 34 | GeoNames batch | REUSE | 1000-size batch؛ resume/restart benchmark pending |
| 35 | import concurrency | BLOCKED | AtomicBoolean only per instance؛ cluster test/design pending |
| 36 | import history | CODE | Admin status and sanitized error؛ DB pending |
| 37 | Startup DB safety | CODE | no auto GeoNames, currency bootstrap explicit only |
| 38 | master data security | CODE | USER_MANAGE in service and network importer |
| 39 | REAL/TEST boundaries | CODE | case REAL + TESTER restriction؛ RLS clone pending |
| 40 | catalog quality | BLOCKED | checksum/version/encoding data QA pending |
| 41 | cache/concurrency | REUSE | no cache added before performance proof |
| 42 | error responses | CODE | 400/403/404/409 and fail closed؛ HTTP integration pending |
| 43 | DTO validation | CODE | @Valid + service policy; full validation integration pending |
| 44 | Query/index performance | BLOCKED | existing indexes reuse; EXPLAIN clone pending |
| 45 | schema/migration | REUSE | V23–V29/V38; no migration needed in patch |
| 46 | golden unit tests | VERIFY | Phase16PolicyTest and other new tests |
| 47 | HTTP/security tests | VERIFY | Case access/FX tests; full MockMvc pending |
| 48 | PostgreSQL isolation tests | BLOCKED | clone/role/RLS needed |
| 49 | UI tests | BLOCKED | TypeScript syntax checked; browser E2E needed |
| 50 | docs | CODE | Implementation/Acceptance/Handoff files |
| 51 | ZIP + installer | CODE | auto-extract, hash guard, backup, verify, rollback |
| 52 | handoff/acceptance | CODE | final status READY_TO_VERIFY, unresolved gates documented |

**این ماتریس تعمداً ۵۲ مورد را «PASS» معرفی نمی‌کند**؛ تا زمان اجرای واقعی تست مربوطه، وضعیت `CODE` صرفاً تحویل منبع است.
