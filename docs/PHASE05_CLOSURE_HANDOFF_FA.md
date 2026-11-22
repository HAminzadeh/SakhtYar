# Phase 05 | Handoff فرآیندهای تجاری E2E-15..20

**مبنای تست:** `Phase05SyntheticFixtureManifest.plan(workspace,run,scenario)`. همه داده‌ها عددی و مصنوعی‌اند. State در TEST تا اثبات محدودیت پایگاه داده `BLOCKED` است؛ هیچ Seed یا Cleanup زنده‌ای اجرا نکنید. E2E-14 پیش‌نیاز کسب‌وکاری است و هنوز Limited Core Fixture است.

| سناریو | Service/Controller واقعی | API و مسیر تست موردنیاز | Fixture Keys | Assertions پیشنهادی و Dependency |
|---|---|---|---|---|
| E2E-15 | `builder/BuilderService`، `BuilderMatchingService`، `BuilderController` | `POST /api/v1/builders/cases/{caseId}`، `POST /api/v1/builders/cases/{caseId}/matches` و GET list؛ 200/403/404 بسته به Auth/ACL | builder,property,owner_a,owner_b | رتبه‌بندی deterministic و cross-case 404؛ وابسته به E2E-14 |
| E2E-16 | `material/MaterialService`، `PriceAnomalyDetector` و `MaterialController` | `POST /api/v1/materials/{id}/prices`، `POST /api/v1/materials/prices/{id}/review` | material,price_observation | negative price رد شود؛ median و anomaly با نسخه قیمت ثابت؛ وابسته به E2E-14 |
| E2E-17 | `assembly/AssemblyService`، `AssemblyCostCalculator` | `POST /api/v1/assemblies/{id}/calculate`، `GET /api/v1/assemblies/{id}/estimates` | assembly,price_observation | effectiveQuantity=27.5، lineTotal=11,000,000 IRR؛ وابسته به E2E-16 |
| E2E-18 | `analysis/feasibility/FeasibilityService` و `FeasibilityController` | `POST /api/v1/feasibility/assessments`، `GET /api/v1/feasibility/cases/{caseId}/assessments` | feasibility,scenario | FAR=3.000000، دسترسی بین پرونده رد؛ وابسته به E2E-17 و E2E-04 |
| E2E-19 | `finance/FinancialAnalysisService`, `FinancialCalculator`, `FinancialAnalysisController` | `POST /api/v1/finance/analyses`, `GET /api/v1/finance/analyses/{id}` | finance_analysis,finance_participant | Revenue 6b, Cost 4b, Profit 2b IRR؛ 60/40؛ zero-cost ROI null؛ وابسته به E2E-15/E2E-18 |
| E2E-20 | `sensitivity/SensitivityService`, `SensitivityCalculator`, `SensitivityController` | `POST /api/v1/sensitivity/analyses`, `GET /api/v1/sensitivity/cases/{caseId}` | sensitivity,snapshot | Shock +10% sale +20% cost، profit=1.88b IRR؛ negative shocks و IDOR؛ وابسته به E2E-19 |

### Handoff اجباری برای پیاده‌سازی Executorهای تجاری بعدی

1. در Runtime، restricted PostgreSQL login فیزیکی با اثبات `session_user=current_user` و deny REAL تأیید شود؛ TESTER application role کافی نیست.
2. شبکه TEST از SMS، Neshan، AI، Redis، MinIO و ایمیل با Adapter واقعی یا deny-at-network ایزوله شود؛ `Phase02OutboundGuard` هنوز سراسری نیست.
3. برای هر Workspace/Run و Case از کلیدهای run-scoped استفاده و پاک‌سازی را دقیقاً به همان namespace محدود کنید.
4. هر Scenario دارای Positive/Negative HTTP، DB evidence، Snapshot version+hash و رد IDOR باشد؛ `progressPercent=100` به معنای PASS نیست.
5. ابتدا E2E-14 و پیش‌نیازهای آن را با شواهد کسب‌وکاری واقعی ببندید؛ سپس پیاده‌سازی ۱۵ تا ۲۰ انجام شود.

**این Handoff مجوز عملیات تغییر دیتابیس یا درخواست HTTP زنده نمی‌دهد.**
