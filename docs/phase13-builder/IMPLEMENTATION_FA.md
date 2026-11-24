# فاز ۱۳ ساخت‌یار — سازندگان، تطبیق، دعوت و پیشنهاد

**مبنای ممیزی:** `v2 @ c82b774595b95905d9c9ee9d0ece2479dda26cf3`، تاریخ `2026-11-22T11:29:53Z`.
**تاریخ بسته:** 2026-11-24. **آخرین Migration پیش از بسته:** `V67`؛ فایل جدید: `V68__builder_invitation_proposal_workflow.sql`.

## ۱. دامنه و محدوده اطمینان

این بسته کد Production، تست‌های محدود و ابزار نصب منبع را تکمیل می‌کند. **هیچ تست واقعی PostgreSQL/MinIO/مرورگر و هیچ Migration روی دیتابیس کاربر در این محیط اجرا نشده است.** داده‌های پرونده، مالک، قرارداد و سازنده در منطق Production از `REAL` خوانده/نوشته می‌شوند؛ این بسته نصب، خود هیچ داده‌ای را درج، اصلاح یا حذف نمی‌کند. اجرای عملیاتی این کد به Migration تأییدشده روی کلون و سپس فرآیند استقرار مصوب نیاز دارد. وضعیت پذیرش اولیه: `READY_TO_VERIFY`، نه `COMPLETE`.

### شکاف‌های اثبات‌شده از آخرین HEAD

| فایل/متد | شاهد واقعی | نقص | اقدام |
|---|---|---|---|
| `BuilderService.create/update` | استفاده از `r.verified()` و نداشتن gate سرویس | تأیید خوداظهاری، تغییر غیرمجاز | بررسی دستیِ مدیر، کنترل permission، audit |
| `BuilderService.list/get` | `BuilderResponse.from` تمام contact و identifiers را برمی‌گرداند | افشای اطلاعات حساس به `CASE_READ` | نمایش عمومی حداقلی، نمایش کامل برای USER_MANAGE |
| `BuilderService.link` | read/check قبل از write بدون lock پرونده | تعارض همزمان، `primary` غیرمجاز | قفل والد، محدودسازی کاندید، انتخاب فقط با پیشنهاد پذیرفته‌شده |
| `BuilderMatchingService.score` | `available` غایب +۲۰؛ sort فقط score | امتیاز مثبت کاذب و tie ناپایدار | نسخه V2، عدم امتیاز برای فقدان شاهد، tie-break UUID |
| `BuilderMatchingService.match` | بارگذاری یک‌باره verified builders | مصرف حافظه نامحدود | پیمایش صفحه‌ای و top-K bounded |
| `BuilderController/DTO` | فقط CRUD/link/matches | نبود دعوت و پیشنهاد | Workflow Controller مستقل و DTO معتبر |
| `V9` | تنها `builder_profile`/`project_builder` | نبود binding/invitation/proposal/revisions | V68 افزایشی بدون تغییر داده قبلی |
| `Phase517CasePanels.tsx` | اتصال مستقیم با role خوداظهاری | نبود تصمیم، مقایسه و دعوت | UI دعوت، پیشنهاد، انتخاب، پیش‌نویس |
| `Phase517BuildersPage.tsx` | صرفاً فهرست | نبود صندوق دعوت و عملیات مدیر | صفحه‌بندی، عملیات مدیر، صندوق دعوت و پاسخ سازنده |

### معماری و مسیر داده

`Identity.app_user` ← (binding با تأیید مدیر) → `BuilderProfile`

`Case/Property` → `BuilderMatchingService` → `BuilderProfile` → `MatchResponse`

`Case creator / ADMIN` → `builder_invitation (PENDING)` → `builder_account_binding` → `ACCEPTED/DECLINED`

`Accepted Invitation` → `builder_proposal SUBMITTED` → `builder_proposal_revision` → `Owner Decision`

`ACCEPT` → `project_builder SELECTED/ACCEPTED (primary=true)` → `ContractService.create(DRAFT only)` → Contract revision history.

تمام ویرایش‌های پرونده‌ای ابتدا روی `construction_case` قفل pessimistic می‌گیرند. عملکرد قبول پیشنهاد در یک تراکنش شامل بروزرسانی Proposal، ProjectBuilder و Audit است. دیتابیس یک سازنده primary و یک پیشنهاد ACCEPTED در هر پرونده را به‌صورت یکتا محدود می‌کند. عملیات بیرونی SMS/Email وجود ندارد. عامل Matching از همان سرویس استفاده می‌کند و موتور تجارت تکرار نشده است.

## ۲. نیازمندی‌ها و کنترل پذیرش ۴۷‌گانه

`S` یعنی کد/پیکربندی سورس پیاده شده ولی آزمون جامع روی ویندوز/کلون لازم است؛ `P` پیاده‌سازی بخشی از قابلیت، `B` نیازمند زیرساخت/شاهد خارجی است. *هیچ S به معنی تست عملیاتی PASSED نیست.*

| # | نیازمندی | وضعیت بسته | شاهد/اقدام و Gate باقی‌مانده |
|---|---|---|---|
| 1 | معماری ماژول | S | Service مستقل سازنده و Workflow؛ Agent service reuse، Maven dependency Contract |
| 2 | مدل اطلاعات سازنده | S | DTO/Policy، فرمت ورودی، metadata محدود؛ Validation در تست‌ها |
| 3 | ثبت سازنده | S | admin-only؛ verified=false؛ duplicate ID و Audit |
| 4 | ویرایش پروفایل | S | مدیر، قفل پروفایل، ممنوعیت direct verified toggle |
| 5 | سازندگان تکراری | S | precheck + unique موجود در V9؛ رقابت DB باید روی clone تست شود |
| 6 | راستی‌آزمایی | P/B | مدیر باید مرجع شاهد بدهد؛ صحت واقعی شاهد همچنان نیاز به فرایند انسانی معتبر دارد |
| 7 | مستندات راستی‌آزمایی | P/B | reference تاریخچه append-only؛ بررسی واقعی Doc provenance/expiry هنوز وابستگی خارجی است |
| 8 | وضعیت فعالیت | P | `verified` و `available` معیار رتبه‌بندی؛ workflow تعلیق جامع/زمان‌دار موجود نیست |
| 9 | ظرفیت و توانمندی | S | validation nonnegative، تجربه، districts، نسخه معیار؛ اطلاعات خوداظهاری |
| 10 | جست‌وجو | P | pagination + city exact؛ full-text indexing و سایر فیلترها بعداً |
| 11 | صفحه‌بندی | S | GET `/builders/page`، count و limit 100؛ Matching page top-K |
| 12 | حریم خصوصی | S | public/redacted profiles و owner-only proposals؛ IDOR test لازم |
| 13 | نقش‌های سازنده/مالک | S/P | binding حساب با کاربر موجود؛ ROLE_BUILDER مستقل موجود نیست، login موجود reuse |
| 14 | اتصال به پرونده | S | case lock، caseAccess، candidate-only/manual versus accepted proposal |
| 15 | سازنده اصلی | S | Unique V9 + lock case + accepted workflow؛ concurrent DB test لازم |
| 16 | چرخه عمر | S | دعوت PENDING/ACCEPTED/DECLINED/CANCELLED/EXPIRED؛ پیشنهاد SUBMITTED/CHANGES_REQUESTED/ACCEPTED/REJECTED/WITHDRAWN |
| 17 | دعوت | S | POST invitation، expiry، dedup؛ بدون SMS/Email واقعی |
| 18 | پاسخ دعوت | S | فقط bound account، قبل از انقضا، read-before-lock projection |
| 19 | ثبت پیشنهاد | S | فقط دعوت پذیرفته‌شده و سازنده bound؛ سهم، مدت، terms |
| 20 | نسخه‌بندی پیشنهاد | S | append-only revision + unique revision number |
| 21 | بازخورد مالک | S | REQUEST_CHANGES از SUBMITTED؛ تغییر terms فقط توسط سازنده |
| 22 | پذیرش/رد | S | owner-only، lock، atomic ProjectBuilder انتخاب، unique accepted |
| 23 | لغو/انصراف | S | cancel pending invite، withdraw pending proposal، audit |
| 24 | مقایسه | S/P | جدول ساختاری UI سهم/مدت/وضعیت؛ تصمیم‌یار پیشرفته مالی هنوز ندارد |
| 25 | الگوریتم Matching | S | مرجع V2، ۱۰+۲۰+۱۰+۲۵+۲۰+۱۰+۵، بدون امتیاز خودکار برای فقدان available |
| 26 | معیارها | S | ورودی metadata خوداظهاری validate، امتیاز ۰–۱۰۰ |
| 27 | وزن‌دهی و نسخه | P | نسخه ثابت `BUILDER_SCORE_V2` در evidence؛ پنل ویرایش وزن‌ها خارج محدوده |
| 28 | tie-break و limit | S | UUID ثابت، limit 1..100، top-K exact across pages |
| 29 | Explainability | S | version/source/disclaimer/reasons/candidateCount |
| 30 | سازندگان نامناسب | S/P | verified-only، unavailable=false excluded؛ سایر thresholdهای مستقل تعریف نشده‌اند |
| 31 | دو سازنده و یک پرونده | S | JUnit synthetic ranking؛ Maven واقعی باید اجرا شود |
| 32 | Property | S | reuse PropertyRepository و CaseAccess؛ property missing error |
| 33 | Owner/Case | S | requireWrite owner/admin، archived reject، serialized case writes |
| 34 | Contract | S | accepted proposal → deterministic unsigned draft via ContractService؛ بدون legal sign |
| 35 | Agents | S | BuilderMatchingTool همان MatchResponse را دریافت می‌کند؛ schema backwards-compatible |
| 36 | Audit | S | actor، event codes، IDs بدون PII؛ نسخه‌های append-only |
| 37 | دیتابیس | S/B | V68 additive، FK, CHECK, unique, RLS scope, trigger؛ clone test الزامی |
| 38 | API/DTO | S | legacy API preserved، new workflow routes، bean Validation + service guards |
| 39 | UI سازندگان | S/P | list/page، city filter، create/review/bind/inbox؛ ویرایش کامل مدیر از طریق API |
| 40 | UI دعوت و پیشنهاد | S/P | invite/inbox/respond/submit همراه شروط مالی، تضمین و تعهدات، مقایسه شروط، decide/draft؛ نمایش نسخه‌ها در UI پرونده و API؛ تست Browser هنوز اجرا نشده |
| 41 | تست امنیت و concurrency | P | synthetic mock negative، DB concurrent race روی clone هنوز اجرا نشده |
| 42 | تست دیتابیس و REAL/TEST | B | PostgreSQL clone, restricted RLS login, no access to shared REAL rows |
| 43 | API/E2E | P | JUnit/Mockito/HTTP test source؛ browser E2E با DB TEST هنوز اجرا نشده |
| 44 | Observability/performance | P | bounded page/top-K، audit؛ benchmark و tracing همچنان نیازمند اجرای واقعی |
| 45 | مستندات | S | README، phase implementation، handoff، OpenAPI route list |
| 46 | Installer/Rollback | S | PS5.1 ZIP auto-extract hash/manifest/backup/verify/rollback؛ Windows test pending |
| 47 | Gate نهایی | B | نیاز به اجرای واقعی Maven/TS build/clone migration/negative E2E و ثبت شواهد |

## ۳. Endpointهای مهم

| متد | مسیر | مجوز Service |
|---|---|---|
| GET | `/api/v1/builders` | CASE_READ؛ مدیر فهرست کامل/بقیه فقط verified redacted |
| GET | `/api/v1/builders/page?page=0&size=20&city=...` | CASE_READ؛ pagination |
| GET | `/api/v1/builders/{id}` | CASE_READ؛ nonmanager only verified redacted |
| POST/PUT | `/api/v1/builders[/{id}]` | USER_MANAGE؛ verified direct rejected |
| POST | `/api/v1/builders/cases/{caseId}/matches` | CASE_READ + CaseAccess |
| POST | `/api/v1/builders/cases/{caseId}` | USER_MANAGE + CASE_WRITE؛ candidate only |
| POST | `/api/v1/builder-workflow/admin/review` | USER_MANAGE؛ human evidence reference |
| POST | `/api/v1/builder-workflow/admin/bind` | USER_MANAGE؛ existing active app_user, one-to-one |
| GET/POST | `/api/v1/builder-workflow/cases/{caseId}/invitations` | case creator/admin write; no outbound messaging |
| GET | `/api/v1/builder-workflow/inbox` | authenticated bound account only |
| POST | `/api/v1/builder-workflow/invitations/{id}/accept` | matched bound account |
| POST | `/api/v1/builder-workflow/invitations/{id}/decline` | matched bound account |
| POST | `/api/v1/builder-workflow/invitations/{id}/cancel` | case creator/admin |
| POST | `/api/v1/builder-workflow/invitations/{id}/proposal` | accepted invite + matched bound account |
| GET | `/api/v1/builder-workflow/invitations/{id}/proposal` | حساب متصل به همان سازنده (پیشنهاد خود) |
| GET | `/api/v1/builder-workflow/cases/{caseId}/proposals` | case creator/admin write |
| GET | `/api/v1/builder-workflow/proposals/{id}/versions` | builder party or case creator/admin |
| POST | `/api/v1/builder-workflow/proposals/{id}/decision` | case creator/admin; ACCEPT/REJECT/REQUEST_CHANGES |
| POST | `/api/v1/builder-workflow/proposals/{id}/withdraw` | submitting builder |
| POST | `/api/v1/builder-workflow/proposals/{id}/contract-draft` | case creator/admin and accepted proposal; unsigned DRAFT |

**حفاظت هویت:** حساب `app_user` باید فعال باشد و مدیر پس از بررسی هویت، ردیف `builder_account_binding` را به یک سازنده نسبت دهد. کاربر هیچ پارامتر `username` برای تصمیم‌گیری پیشنهاد نمی‌تواند به‌عنوان هویت خود تحمیل کند. نقش مستقل `BUILDER` در Identity نداریم؛ امنیت دعوت/پیشنهاد در Service بر اساس اتصال یک‌به‌یک و احراز هویت موجود انجام می‌شود.

**وضعیت امضا:** هیچ API امضای الکترونیک یا پذیرش رسمی قرارداد وجود ندارد؛ `ACCEPTED` فقط تصمیم مقدماتی روی پیشنهاد است. فرایند حقوقی نیازمند بازبینی مستقل و سرویس امضای معتبر خواهد بود.

## ۴. Migration / ایزوله‌سازی / Rollback

* آخرین Migration موجود GitHub در ممیزی: `V67`؛ فایل جدید `V68` را هرگز جایگزین شماره دیگر نکنید.
* نصب‌کننده PowerShell فقط سورس را جایگزین می‌کند؛ `mvn test` و `npm run build` جایگزین Migration روی کلون نیستند.
* فایل `application-local.yml` عمداً Flyway را غیرفعال می‌کند. `application-development.yml` و `application-production.yml` نیز در HEAD مورد ممیزی Flyway خاموش دارند. اما `application-server.yml` و مقدار پایه `application.yml` می‌توانند Flyway را فعال کنند؛ قبل از Start حتماً profile/override را بررسی کنید.
* **با وجود Flyway غیرفعال، `ddl-auto: validate` ممکن است هنگام نبودن جدول‌های V67/V68 استارت Backend را متوقف کند.** این یک وضعیت Fail-closed است، نه اجازه اجرای Migration روی دیتابیس اصلی.
* V68 فقط CREATE TABLE/INDEX/FUNCTION/TRIGGER/RLS/GRANTS دارد، بدون UPDATE/DELETE/INSERT روی جدول‌های قبلی. اجرای Schema هم تغییر واقعی DB محسوب می‌شود.
* روی کلون: از DB اصلی Snapshot/Backup و clone ایزوله با کاربر غیر-superuser تهیه کنید؛ V67 و V68 را با Flyway تحت کنترل نسخه اعمال کنید؛ FK، Triggerهای immutable، concurrent accepted و RLS/Workspace را با TEST مصنوعی بررسی کنید؛ سپس با تصویب مالک DB برای محیط عملیاتی برنامه‌ریزی کنید.
* برگشت سورس: `Rollback` نصب‌کننده از backup خارجی فقط هنگامی مجاز است که فایل‌های نصب‌شده به‌صورت مستقل تغییر نکرده باشند. Flyway rollback خودکار ندارد؛ تغییر Schema روی DB اصلی نیازمند طرح بازگشت DBA و Backup واقعی است.

## ۵. آزمون‌های مورد انتظار

`mvn -o -pl modules/builder -am clean test -Dtest=Phase05BuilderMatchingAccessTest,Phase13BuilderPolicyTest,Phase13BuilderMatchingTest,Phase13BuilderServiceSafetyTest,Phase13BuilderEngagementTest,Phase13BuilderWorkflowHttpTest -Dsurefire.failIfNoSpecifiedTests=false`

`npm run build` از پوشه `frontend` برای بررسی کامل TypeScript/Vite.

- **تست‌های Java کدنویسی‌شده:** policy negative، authorization، redaction، fake verification، ۲ سازنده و ۱ پرونده، Stable ranking، case IDOR، invite access، rejected selection، transaction reuse، MockMvc.
- **اجرای واقعی این تست‌های JUnit:** در محیط تحویل Linux به علت نبود Maven/وابستگی Spring اجرا نشده. نصب‌کننده Windows آن‌ها را با Maven `clean test` واقعی اجرا و XML Surefire را کنترل می‌کند.
- **Java 21 offline smoke:** فقط اعتبارسنجی policy و compilation با Stub؛ نه Spring integration.
- **بسته نصب:** هیچ `docker compose`, `flyway migrate`, SQL execution یا تغییر DB ندارد.

## ۶. تصمیم‌های عمدی و باقی‌مانده

1. پذیرش پیشنهاد، *انتخاب مقدماتی* است؛ قرارداد را امضا نمی‌کند.
2. `BuilderProfile.verified` حتی با بررسی مدیریتی به معنای صلاحیت قضایی یا ثبت رسمی نیست. API مدارک را به طور رمزنگاری‌شده اعتبارسنجی نمی‌کند؛ تنها مرجع بررسی اجباری و Audit ثبت می‌کند.
3. ارسال SMS/Email عمداً پیاده‌سازی نشده؛ صندوق دعوت داخل برنامه وجود دارد و به هویت واقعی کاربر bind شده است.
4. پیشنهادهای اقتصادی فقط فیلدهای توافق‌شده محدودی دارند: سهم، مدت و JSON terms. مدل ارزش‌گذاری حقوقی/مالی پیشرفته نیازمند مشخصات تاییدشده است.
5. خروجی Candidate Score از اطلاعات metadata سازنده خوداظهاری است. `BUILDER_SCORE_V2` امتیاز، دلیل و منبع را نشان می‌دهد، اما تأیید صحت اطلاعات را تضمین نمی‌کند.
6. فهرست پیشرفته full-text، تست Browser نمایش تاریخچه ویرایش پیشنهاد و تأیید اسناد واقعی هنوز Gate تکمیلی دارند؛ به‌عنوان تکمیل‌شده گزارش نشوند.
7. محافظت از RLS در `builder_invitation` و `builder_proposal` در سطح REAL/TEST است؛ تفکیک کاربران در سرویس انجام می‌شود و اثر روی نقش دیتابیس باید روی کلون با نقش محدود آزموده شود.
8. Snapshotهای V68 فقط در صورتی که Trigger/RLS مورد انتظار روی clone تأیید شوند قابلیت اتکای فنی دارند؛ مدرک حقوقی محسوب نمی‌شوند.
