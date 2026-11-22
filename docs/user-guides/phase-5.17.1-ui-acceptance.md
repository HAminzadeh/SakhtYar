# SakhtYar UI and user guidance – Phase 5.17.1

## Scope and compatibility

This update improves existing React views on branch v2. It does not introduce database migrations, identity-provider changes or backend security-policy changes.

## Header and navigation

- The brand remains at the right of the RTL header. The main menu is placed immediately next to it in a bounded, horizontally scrollable column, while the language/profile controls remain independent.
- The label is correctly spelled «آموزش». At narrower widths, navigation occupies its own row and does not cover the language selector.

## Folder-scoped document discovery

- Navigate to the folder in «مرکز دانش» and check the selected source path.
- «باز کردن» now synchronizes the browsing location and the actual source folder.
- «کشف و لیست اسناد» passes the visible selected folder as sourceRoot to the existing API.
- Server implementation resolves sourceRoot and recursively discovers documents within that folder and any nested subfolders. Parent and sibling folders are not part of that execution.
- Test manually using two sibling directories with distinct filenames.
- New source selections always create a separate execution; retry/resume previous executions only when appropriate.

## Knowledge center tabs

- Before starting: «پوشه و اجرا»، «اجراهای اخیر»، «راهنما».
- During/after an execution: «وضعیت و پیشرفت»، «اسناد و انتخاب»، «لاگ و Checkpoint»، «نتایج»، «راهنما».
- Checkpoints and event logs remain available; result explorer still uses the existing backend.

## Settings tabs

- User preferences: locale/country/currency/timezone separated from appearance/theme. Save & Verify is shared and requires explicit confirmation.
- Configuration center: settings/services separated from automation, doctor, effective values and version history. Existing category navigation remains inside service settings.
- Secret values remain masked. No new secret logging is introduced.

## Account sessions

- The existing sessions endpoint is unchanged. Results are paginated client-side in 10-item pages, with the existing revoke actions retained.

## In-app help and project documentation

- Detailed, actionable guides are in frontend/src/ui/helpTopics.ts and surfaced at /help.
- Contextual Help buttons navigate to /help?topic=<topic> for market, knowledge, account, and settings.
- Supporting operator manual: docs/user-guides/phase-5.17.1-operation-manual.md.

## Acceptance checklist

1. At 1920, 1366 and 1024 pixel widths, header brand, menu and language selector must not overlap.
2. Confirm «آموزش» label and route /help.
3. Pick folder B nested under A, click «باز کردن», click discover, confirm source_root=B and no documents from A siblings.
4. Confirm all knowledge tabs render and old run controls remain functional.
5. Confirm accounts with more than 10 sessions paginate at 10 per page; revoke works.
6. Confirm locale/theme changes persist only on Save & Verify.
7. Confirm config editor, Doctor and Automation continue to work on their respective tabs.
8. Confirm contextual information opens the matching documentation topic.
9. Run npm run build and git diff --check. Conduct browser smoke tests with real authenticated data.

## Release gates

- Live UI workflows are not proven by source builds alone.
- Per-case authorization remains a previously identified P0 release blocker.
- Production SSO, external notification delivery and source-verified market observations are separate prerequisites.
