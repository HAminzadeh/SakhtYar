SakhtYar Phase 1 UI patch
=========================

Prerequisite:
The Phase 1 Property/Owners backend patch and Flyway V3 must already be applied.

Copy this package over the repository root:
D:\ChatGPT_Projects\SakhtYar\source

New frontend files:
- frontend/src/features/case/CaseOverview.tsx
- frontend/src/features/case/PropertyPanel.tsx
- frontend/src/features/case/OwnersPanel.tsx
- frontend/src/features/case/DocumentsPanel.tsx
- frontend/src/features/case/MapPlaceholder.tsx

Replaced frontend files:
- frontend/src/pages/CaseDetailPage.tsx
- frontend/src/api/types.ts
- frontend/src/api/client.ts

New documentation:
- docs/architecture/05-phase1-ui.md

Run:
1) cd frontend
2) npm install
3) npm run build
4) npm run dev

Open:
http://localhost:5173

Smoke test:
1) Login.
2) Open an existing case.
3) Open "مشخصات ملک".
4) Save/update property data.
5) Refresh and confirm the values persist.
6) Open "مالکین".
7) Add an owner with a share such as 1/2.
8) Add another owner with 1/2.
9) Confirm total ownership shows 100%.
10) Edit one owner.
11) Confirm only one primary contact exists.
12) Upload/download a document from the "مدارک" tab.

The "نقشه" tab is a placeholder for the next iteration.
