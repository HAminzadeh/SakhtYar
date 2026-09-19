# Phase 1 UI - Case Workspace

The Phase 1 frontend turns the Case detail page into a workspace with tabs.

## Tabs

- Overview
- Property
- Owners
- Map placeholder
- Documents

## Property

The Property tab reads and writes:

```text
GET /api/v1/cases/{caseId}/property
PUT /api/v1/cases/{caseId}/property
```

It supports:

- province
- city
- district
- neighborhood
- address
- land area
- registry main/sub number
- registry section
- postal code
- latitude/longitude

## Owners

The Owners tab uses:

```text
GET    /api/v1/cases/{caseId}/owners
POST   /api/v1/cases/{caseId}/owners
PUT    /api/v1/cases/{caseId}/owners/{ownerId}
DELETE /api/v1/cases/{caseId}/owners/{ownerId}
```

It provides:

- create/edit/delete owner
- ownership fraction
- percentage preview
- total ownership progress
- primary contact
- UI guard against ownership totals above 100%

The backend remains the final authority for validation.

## Documents

The existing MinIO/PostgreSQL document workflow is moved into a reusable
`DocumentsPanel` component without changing its API behavior.

## Map

The Map tab is intentionally a placeholder in this iteration. The Property
model already contains latitude and longitude. The next iteration will add
map selection and the Neshan adapter.
