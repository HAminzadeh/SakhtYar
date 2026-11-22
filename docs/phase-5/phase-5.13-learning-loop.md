# Phase 5.13 - Learning Loop

## Purpose

Phase 5.13 creates a controlled feedback loop between Persian input, user corrections
and the Shared Knowledge Platform.

The core trust rule remains unchanged:

**feedback is evidence, not authority.**

Nothing learned from a user, agent or model is automatically promoted to approved
knowledge.

## Learning event

Each feedback submission is persisted as an immutable `learning_event`.

A learning event can reference:

- construction case
- agent conversation
- Persian Input Gateway request
- feedback type
- corrected field/value
- response rating
- note
- optional Knowledge Candidate
- structured metadata
- actor and timestamp

## Supported feedback types

- `UNKNOWN_TERM`
- `TERM_CORRECTION`
- `PARAMETER_CORRECTION`
- `INTENT_CORRECTION`
- `RESPONSE_RATING`
- `OTHER`

## Knowledge candidate creation

Only explicit feedback can request candidate creation.

Candidate creation is limited to:

- `UNKNOWN_TERM`
- `TERM_CORRECTION`

Supported candidate types in this phase:

- `TERM`
- `ALIAS`
- `DEFINITION`

Candidates are created with:

- origin `USER_INPUT`
- status `PENDING_REVIEW`

They still require the normal Shared Knowledge Platform review workflow before they
can become authoritative.

## Non-knowledge feedback

Parameter corrections, intent corrections and response ratings are stored as
learning evidence but are not automatically converted into knowledge terms.

This prevents numeric corrections, one-off project facts and subjective ratings
from polluting the Knowledge Platform.

## Traceability

The loop is:

Persian Input Gateway request
-> user feedback
-> learning event
-> optional knowledge candidate
-> human review
-> approved/rejected knowledge

## Immutability

Phase 5.13 exposes create/read operations only for learning events.

No update/delete API is added.

## Deferred

- automatic model fine-tuning
- automatic prompt mutation
- automatic candidate approval
- automatic unknown-token candidate creation
- ranking model training
- feedback analytics dashboard
- admin learning-center UI