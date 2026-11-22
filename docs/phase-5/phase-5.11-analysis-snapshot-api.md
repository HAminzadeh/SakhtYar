# Phase 5.11 â€” Analysis Snapshot API

Base path: `/api/v1/analysis-snapshots`

## Create
`POST /api/v1/analysis-snapshots`

Example:
```json
{
  "caseId": "00000000-0000-0000-0000-000000000000",
  "scenarioId": "00000000-0000-0000-0000-000000000000",
  "analysisType": "FINANCIAL",
  "status": "FINALIZED",
  "schemaVersion": "1.0",
  "calculationEngineVersion": "1.0",
  "knowledgeVersion": null,
  "regulationVersion": "1.0",
  "materialPriceVersion": "1.0",
  "rootEntityType": "FINANCIAL_ANALYSIS",
  "rootEntityId": "00000000-0000-0000-0000-000000000000",
  "parentSnapshotId": null,
  "inputSnapshot": {
    "financialAnalysisId": "00000000-0000-0000-0000-000000000000"
  },
  "resultSnapshot": {
    "projectedProfit": "1000000"
  },
  "metadata": {},
  "lineage": [
    {
      "outputPath": "$.resultSnapshot.projectedProfit",
      "relationshipType": "DERIVED_FROM",
      "sourceType": "ENTITY",
      "sourceEntityType": "FINANCIAL_ANALYSIS",
      "sourceEntityId": "00000000-0000-0000-0000-000000000000",
      "sourceUrl": null,
      "sourceLabel": "Phase 5.9 financial analysis",
      "sourceObservedAt": null,
      "sourceVersion": "1.0",
      "sourceHash": null,
      "confidence": 1.0,
      "metadata": {}
    }
  ]
}
```

## Read
- `GET /api/v1/analysis-snapshots/{id}`
- `GET /api/v1/analysis-snapshots/cases/{caseId}`
- `GET /api/v1/analysis-snapshots/cases/{caseId}?analysisType=FINANCIAL`
- `GET /api/v1/analysis-snapshots/scenarios/{scenarioId}`
- `GET /api/v1/analysis-snapshots/{id}/lineage`

Snapshots and lineage are create/read only in this phase.