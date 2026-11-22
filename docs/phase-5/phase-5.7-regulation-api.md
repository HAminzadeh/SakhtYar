# Phase 5.7 â€” Regulation API

Base path: `/api/v1/regulations`

## Rules
- `GET /api/v1/regulations/rules`
- `POST /api/v1/regulations/rules`
- `PUT /api/v1/regulations/rules/{id}`

Example MAX_FLOORS parameters:

```json
{
  "maxFloors": 5
}
```

Example MAX_FAR parameters:

```json
{
  "maxFar": 2.5
}
```

## Evaluation
- `POST /api/v1/regulations/evaluate`

Example:

```json
{
  "propertyId": "00000000-0000-0000-0000-000000000000",
  "scenarioId": null
}
```

## History
- `GET /api/v1/regulations/properties/{propertyId}/evaluations`
- `GET /api/v1/regulations/evaluations/{id}`

Missing required inputs are returned as REVIEW outcomes rather than inferred values.