# Phase 5.5 â€” Assembly API

Base path: `/api/v1/assemblies`

## Assemblies
- `GET /api/v1/assemblies`
- `GET /api/v1/assemblies/{id}`
- `POST /api/v1/assemblies`
- `PUT /api/v1/assemblies/{id}`

## Components
- `GET /api/v1/assemblies/{id}/components`
- `POST /api/v1/assemblies/{id}/components`
- `DELETE /api/v1/assemblies/{id}/components/{componentId}`

A component references a Phase 5.4 material and optionally a material variant.

## Cost calculation
- `POST /api/v1/assemblies/{id}/calculate`

Example request:

```json
{
  "quantity": 100,
  "priceType": "MARKET_QUOTE",
  "currencyCode": "IRR",
  "province": "Tehran",
  "city": "Tehran"
}
```

The engine requires a compatible material price aggregate for every assembly component.

## Historical estimates
- `GET /api/v1/assemblies/{id}/estimates`
- `GET /api/v1/assemblies/estimates/{id}`

Historical estimates retain references to the exact price aggregates used during calculation.