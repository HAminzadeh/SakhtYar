# Phase 5.13.5.1 - Global Foundation Hardening

This pass closes the remaining gaps before Phase 5.14.

- Complete existing UI language switching with a legacy-UI compatibility localizer.
- Persian uses RTL/fa-IR and English uses LTR/en-US.
- Demo project names and common demo locations have English localized forms.
- Date/number formatting no longer hardcodes fa-IR.
- Authenticated server preferences override browser defaults after login/session restore.
- Themes are functional: SYSTEM, LIGHT, DARK, OCEAN, EMERALD, SUNSET, MIDNIGHT.
- SYSTEM follows the operating-system light/dark preference.
- Admin Settings exposes master-data counts and asynchronous GeoNames synchronization.
- Persian Input Gateway knowledge retrieval filters terms and aliases by case jurisdiction.
- Currency entities expose read-only currency_id FK while keeping currency_code as historical/display snapshot.
- Currency Java length constraints support custom values such as TOMAN.
- TypeScript .tsbuildinfo is no longer tracked.

User-entered business content is preserved as entered unless a localized variant is explicitly available; the UI itself is localized.