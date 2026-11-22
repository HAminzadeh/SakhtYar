# SakhtYar Phase 5.17 - Integration release

## Implementation delivered by the script

1. Validate pinned Git branches, source anchors, track changed files and produce rollback snapshots.
2. CI Dockerfile CA bootstrap before HTTPS APT, update migration smoke check through V46.
3. BFF public read-only `/public/capabilities` and authenticated `/market/comparables` with bounds.
4. WordPress PHP analytics mapped to real BFF `{summary,trend,snapshot}` responses.
   No synthetic prices/signals for operational display. Cached for five minutes.
5. WordPress admin connectivity settings and admin-only `/bridge/health` diagnostic.
6. WordPress account CTA to React app, explicitly NOT a SSO assertion.
7. React market dashboard with source freshness, statistics, samples and comparables.
8. Admin React builder directory; builder matching and contract draft/review panels by case.
9. Admin monitoring rules and notification-outbox read-only UI.
10. Agent descriptor and component registry UI, permission-gated routes and navigation.
11. Documentation: public API, identity, security and runtime environment templates.
12. Local source verification, optional Maven/TypeScript/PHP tests, website release packaging and execution manifest.

## Manual / external delivery gates -- NOT represented as completed

- **True SSO**: select/register an OIDC Identity Provider; implement/test Java resource-server
  trust and WordPress account linking; deploy auth code+PKCE clients and migration plan.
- **Real notification delivery**: SMTP/SMS provider onboarding and worker implementation,
  retry/idempotency policy, delivery callbacks and failure testing.
- **Authoritative data**: verified municipal regulations, fresh market and material datasets,
  expert review and case-level provenance. The code cannot invent these.
- **Runtime infrastructure**: configure real HTTPS URL and gateway/Cloudflare Tunnel,
  deploy to Staging, migrate V46, perform cross-origin auth and E2E checks.
- **Full intelligence workflow**: supply approved DAG definitions and realistic data,
  validate task retries/approvals/human-in-the-loop before declaring operational readiness.
- **Production**: secret management, least privilege, traffic limits, backups+restore,
  threat modeling, observability alerts and rollback rehearsal.

## Version compatibility

- Intended Backend branch: `v2` with Phase 5.16 source layout.
- Intended Website branch: `main`, Theme 0.6.2 and Core Plugin 0.6.1.
- WP plugin is incremented to 0.7.0 by this patch; existing theme is preserved.
- No automatic Git push, merge or production deployment.

## Recommended functional smoke sequence

1. On staging configure WordPress `syf_backend_base_url` to a verified HTTPS API origin.
2. Confirm `GET /api/web/v1/public/capabilities` responds; verify WP Bridge admin status.
3. Ingest real, source-attributed market records through secured admin APIs.
4. Confirm `GET /api/web/v1/public/market?city=...&district=...&days=30` reports a nonzero sample count.
5. Open WordPress `/market-analytics/` with a permitted user; ensure display matches Java values.
6. Log into React using Java Identity; load `/market`, case matching, contracts and admin views.
7. Ensure unauthorized user cannot fetch private cases or operational admin endpoints.
   The original CaseService list/get has global queries; multi-tenant authorization
   is a HIGH-SEVERITY OPEN GATE until scoped, audited and tested.
8. Verify no market record => explicit unavailable/insufficient data, NOT a fabricated price.
9. Run backup+restore drill and CI on new commit before staging->production promotion.