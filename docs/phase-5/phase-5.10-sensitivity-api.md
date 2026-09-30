# Phase 5.10 â€” Sensitivity API

Base path: `/api/v1/sensitivity`

## Create analysis
`POST /api/v1/sensitivity/analyses`

Example:
```json
{
  "financialAnalysisId": "00000000-0000-0000-0000-000000000000",
  "points": [
    {
      "salePriceChangePercent": 0,
      "sellableAreaChangePercent": 0,
      "constructionCostChangePercent": 0
    },
    {
      "salePriceChangePercent": -10,
      "sellableAreaChangePercent": 0,
      "constructionCostChangePercent": 15
    },
    {
      "salePriceChangePercent": 10,
      "sellableAreaChangePercent": 5,
      "constructionCostChangePercent": 0
    }
  ]
}
```

## Read
- `GET /api/v1/sensitivity/analyses/{id}`
- `GET /api/v1/sensitivity/financial-analyses/{financialAnalysisId}`
- `GET /api/v1/sensitivity/cases/{caseId}`

Maximum explicit points per request: 250.