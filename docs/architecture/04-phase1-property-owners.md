# Phase 1 - Property and Owners

This iteration extends the Phase 0 case workspace with structured property and ownership data.

## Scope

- One Property per Case
- Multiple Owners per Property
- Ownership fraction per Owner
- One optional primary contact
- Property address and registry metadata
- Latitude/longitude prepared for the next Map iteration
- Audit events for create/update/delete operations
- Backfill existing Phase 0 case location data into Property

## API

```text
GET    /api/v1/cases/{caseId}/property
PUT    /api/v1/cases/{caseId}/property

GET    /api/v1/cases/{caseId}/owners
POST   /api/v1/cases/{caseId}/owners
PUT    /api/v1/cases/{caseId}/owners/{ownerId}
DELETE /api/v1/cases/{caseId}/owners/{ownerId}
```

## Important compatibility note

During this iteration, `construction_case.city`, `district`, `address`, and
`land_area_m2` remain in place because the current Phase 0 frontend still reads
those fields.

`PropertyService` synchronizes the Property values back to these legacy
summary fields. They can be removed in a later migration after the frontend
uses the Property API everywhere.

## Next iteration

- Property/Owners frontend tabs
- Map integration
- Coordinate selection
- PostGIS geography point
