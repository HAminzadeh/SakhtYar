# Phase 5.2 â€” Knowledge API

Base path: `/api/v1/knowledge`

## Terms
- `GET /terms`
- `GET /terms/{id}`
- `POST /terms`
- `PUT /terms/{id}`
- `GET /terms/{id}/revisions`

Filters for `GET /terms`:
- `query`
- `domain`
- `status`

## Aliases
- `GET /terms/{id}/aliases`
- `POST /terms/{id}/aliases`

Aliases are normalized deterministically for Persian matching.

## Relations
- `GET /terms/{id}/relations`
- `POST /relations`

## Candidates / review queue
- `GET /candidates`
- `POST /candidates`
- `POST /candidates/{id}/review`

Candidate review currently accepts `APPROVED` or `REJECTED`. Approval changes only the candidate review state; it does not automatically create or mutate an authoritative term.

## UI
No full Knowledge Review Center is introduced in Phase 5.2. The admin UI is planned for Phase 5.18.