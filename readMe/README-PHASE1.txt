SakhtYar Phase 1 backend patch
==============================

Copy the contents of this package over the repository root.

New files:
- V3__property_and_owner_schema.sql
- property module
- owner module
- Phase 1 architecture document

Replaced file:
- backend/src/main/java/com/sakhtyar/casefile/domain/CaseEntity.java
  (adds syncPropertySnapshot for temporary Phase 0 UI compatibility)

After copying:
1) Reload Maven in IntelliJ.
2) Run Maven clean + test/install.
3) Start SakhtYarApplication.
4) Check the log for Flyway migration V3.
5) Test:
   GET /api/v1/cases/{caseId}/property
   GET /api/v1/cases/{caseId}/owners

Do not rename V3 after it has been applied to a database.
