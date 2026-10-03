# Phase 5.13 - Learning Loop API

Base path:

`/api/v1/agents/learning`

The path remains under `/api/v1/agents/**`, so the existing `AGENT_USE`
authorization policy continues to apply.

## Submit feedback

`POST /api/v1/agents/learning/feedback`

Example:

```json
{
  "caseId": "10000000-0000-0000-0000-000000000001",
  "conversationId": null,
  "inputGatewayRequestId": null,
  "feedbackType": "UNKNOWN_TERM",
  "fieldKey": null,
  "originalValue": "unknown Persian term",
  "correctedValue": "reviewable Persian term",
  "rating": null,
  "note": "User clarified the domain meaning.",
  "proposeKnowledgeCandidate": true,
  "candidateType": "TERM",
  "proposedTermCode": null,
  "proposedNameFa": null,
  "proposedNameEn": null,
  "confidence": 0.80,
  "metadata": {}
}
```

If `proposeKnowledgeCandidate=true`, the candidate remains `PENDING_REVIEW`.

## Read one learning event

`GET /api/v1/agents/learning/events/{id}`

## Read case learning history

`GET /api/v1/agents/learning/cases/{caseId}`

## Read feedback for one Persian Input Gateway request

`GET /api/v1/agents/learning/input-requests/{inputRequestId}`

## Safety rules

- response ratings require a rating from 1 to 5
- correction feedback requires `correctedValue`
- only unknown-term and term-correction feedback can propose knowledge candidates
- no learning event directly modifies approved knowledge