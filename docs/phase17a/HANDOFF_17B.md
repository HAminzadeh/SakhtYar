# HANDOFF فاز ۱۷-A به ۱۷-B

- Baseline GitHub: `v2@8936cbdbef08e2ee9f211b3ce29fe3f83f2f702d`.
- قبل از شروع B: آخرین Git HEAD جدید را بخوان؛ خروجی نصب/Verify کاربر و `git status` را دریافت و Releaseهای جدید DB را فقط‌خواندنی بررسی کن.
- Candidate preview API: `/api/v1/knowledge/admin/phase17/bridge/candidates`، فقط مدیر، غیرالزام‌آور، خواندنی.
- Zero legal upgrade, zero promotion, zero UrbanRule activation in A. Historical audit: ۲٬۵۹۶ LAW از ۹۳۴ RULE و ۱٬۶۶۲ SEGMENT؛ در زمان snapshot Verified/Published/UrbanRule برابر صفر.
- Gateهای باز: (۱) Case-scenario ownership: ConstructionScenario entity global without caseId. (۲) V45 city-not-null check: nationwide rules need reviewed additive migration and clone testing. (۳) Exact SHA/page match must be verified against original PDF, not only db metadata. (۴) Persistent Bridge promotion/review/graph must be designed without auto-publication. (۵) Rerun pipeline cross-instance transaction and concurrency. (۶) Real Maven tests and PostgreSQL clone/E2E.
- در فاز B: Snapshot Rule و متعلقات ماده/تبصره، نسخه حقوقی، تعارض، Revocation، مسئولیت بازبین/منتشرکننده، Conversion و UrbanRule فقط با Approval مستقل، با تست.
- No real data modification without explicit authorization. No auto Flyway/DB operations in installer.
