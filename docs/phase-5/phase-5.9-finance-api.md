# Phase 5.9 â€” Finance API

Base path: `/api/v1/finance`

## Create financial analysis
`POST /api/v1/finance/analyses`

Example:
```json
{
  "feasibilityAssessmentId": "00000000-0000-0000-0000-000000000000",
  "sellableAreaM2": 1200,
  "expectedSalePricePerM2": 150000000,
  "currencyCode": "IRR",
  "otherRevenue": 0,
  "additionalCost": 5000000000,
  "financingCost": 0,
  "taxesAndFees": 2000000000,
  "participants": [
    {
      "participantType": "OWNER",
      "participantRefId": "00000000-0000-0000-0000-000000000000",
      "participantLabel": "Owner",
      "valueSharePercent": 45,
      "costSharePercent": 0,
      "metadata": {}
    },
    {
      "participantType": "BUILDER",
      "participantRefId": "00000000-0000-0000-0000-000000000000",
      "participantLabel": "Builder",
      "valueSharePercent": 55,
      "costSharePercent": 100,
      "metadata": {}
    }
  ]
}
```

## Read
- `GET /api/v1/finance/analyses/{id}`
- `GET /api/v1/finance/cases/{caseId}/analyses`
- `GET /api/v1/finance/scenarios/{scenarioId}/analyses`

Value shares and cost shares must each independently total 100%.