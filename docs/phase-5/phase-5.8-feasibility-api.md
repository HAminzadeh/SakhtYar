# Phase 5.8 â€” Feasibility API

Base path: `/api/v1/feasibility`

## Create assessment
`POST /api/v1/feasibility/assessments`

Example:
```json
{
  "caseId": "00000000-0000-0000-0000-000000000000",
  "scenarioId": "00000000-0000-0000-0000-000000000000",
  "scenarioCostSnapshotId": "00000000-0000-0000-0000-000000000000",
  "urbanEvaluationId": "00000000-0000-0000-0000-000000000000"
}
```

## Read
- `GET /api/v1/feasibility/assessments/{id}`
- `GET /api/v1/feasibility/cases/{caseId}/assessments`
- `GET /api/v1/feasibility/scenarios/{scenarioId}/assessments`

The API intentionally requires exact cost/regulation snapshot IDs so an assessment remains reproducible.