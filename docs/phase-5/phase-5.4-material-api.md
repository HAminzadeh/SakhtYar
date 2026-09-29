# Phase 5.4 â€” Material API

Base path: `/api/v1/materials`

## Material catalog
- `GET /api/v1/materials`
- `GET /api/v1/materials/{id}`
- `POST /api/v1/materials`
- `PUT /api/v1/materials/{id}`

## Variants
- `GET /api/v1/materials/{id}/variants`
- `POST /api/v1/materials/{id}/variants`

## Price observations
- `GET /api/v1/materials/{id}/prices`
- `POST /api/v1/materials/{id}/prices`
- `POST /api/v1/materials/prices/{id}/review`

New observations are always `PENDING_REVIEW`.

## Price aggregates
- `GET /api/v1/materials/{id}/aggregates`
- `POST /api/v1/materials/{id}/aggregates/rebuild`

Only approved observations are aggregated.

## Anomalies
- `GET /api/v1/materials/anomalies`
- `GET /api/v1/materials/anomalies?status=OPEN`
- `POST /api/v1/materials/anomalies/{id}/review`

Anomalies are advisory review signals and do not automatically alter source observations.