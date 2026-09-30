# Phase 5.6 â€” Scenario API

Base path: `/api/v1/scenarios`

Quality packages:
- GET `/quality-packages`
- POST `/quality-packages`
- PUT `/quality-packages/{id}`
- GET `/quality-packages/{id}/selections`
- POST `/quality-packages/{id}/selections`
- DELETE `/quality-packages/{id}/selections/{selectionId}`

Scenarios:
- GET `/api/v1/scenarios`
- POST `/api/v1/scenarios`
- PUT `/api/v1/scenarios/{id}`
- POST `/api/v1/scenarios/{id}/status`

Items:
- GET `/api/v1/scenarios/{id}/items`
- POST `/api/v1/scenarios/{id}/items`
- DELETE `/api/v1/scenarios/{id}/items/{itemId}`

Cost:
- POST `/api/v1/scenarios/{id}/calculate`
- GET `/api/v1/scenarios/{id}/snapshots`