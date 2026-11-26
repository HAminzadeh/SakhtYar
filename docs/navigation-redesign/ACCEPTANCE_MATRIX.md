# Navigation R01 — Acceptance Matrix

| ID | Requirement | Evidence | Status before Windows Verify |
|---|---|---|---|
| NAV01 | Replace flat main navigation with permission-filtered groups | `frontend/src/app/AppShell.tsx` | READY_TO_VERIFY |
| NAV02 | Preserve every defined top-level route | `frontend/scripts/verify-navigation.mjs` | READY_TO_VERIFY |
| NAV03 | Responsive mobile Drawer and account menu | `AppShell.tsx`, `navigation-workflow.css` | READY_TO_VERIFY |
| NAV04 | Four clear sequential case workflow groups | `CaseDetailPage.tsx` | READY_TO_VERIFY |
| NAV05 | Retain all original twelve case panels and permissioned assistant | `CaseDetailPage.tsx` | READY_TO_VERIFY |
| NAV06 | Stage-specific submenus and suggested next section | `CaseDetailPage.tsx` | READY_TO_VERIFY |
| NAV07 | Preserve URL on refresh and browser history | `useSearchParams()` in `CaseDetailPage.tsx` | READY_TO_VERIFY |
| NAV08 | Avoid falsely marking stages complete | Four navigation cards with explicit explanatory text | READY_TO_VERIFY |
| NAV09 | Persian/RTL, keyboard focus and responsive rendering | Scoped CSS, browser checklist | READY_TO_VERIFY |
| NAV10 | Source guards, backup, rollback, ZIP verification | Windows PowerShell installer | READY_TO_VERIFY |
| NAV11 | TypeScript compile and Vite Build | Installer Verify / `npm run build` | READY_TO_VERIFY |
| NAV12 | Browser, responsive and permission regression | Browser + role-matrix manual checks | BLOCKED_BROWSER |

Original Phase 17-A/B/C legal, DB and test acceptance items are unaffected; this navigation patch does **not** close them.
