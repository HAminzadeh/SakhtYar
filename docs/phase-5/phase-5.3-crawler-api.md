# Phase 5.3 â€” Crawler API

Base path: `/api/v1/crawler`

## Sources
- `GET /sources`
- `GET /sources/{id}`
- `POST /sources`
- `PUT /sources/{id}`

Each crawler source references an existing `knowledge_source`.

## Execution
- `POST /sources/{id}/run`

Optional body:
```json
{
  "maxPagesOverride": 10
}
```

The run endpoint is synchronous in Phase 5.3. A background scheduler/queue can be added later without changing the persisted job model.

## Jobs and evidence
- `GET /sources/{id}/jobs`
- `GET /jobs/{id}/documents`

## Knowledge boundary
New changed text documents create `knowledge_candidate` records with:
- origin: `CRAWLER`
- status: `PENDING_REVIEW`

No crawler endpoint approves or edits authoritative knowledge.