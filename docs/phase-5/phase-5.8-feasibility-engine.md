# Phase 5.8 â€” Feasibility Engine

## Purpose
Phase 5.8 composes reviewed outputs from Property, Construction Scenario, Scenario Cost and Urban Regulation into one deterministic feasibility assessment.

This phase deliberately does not calculate revenue, profit, owner/builder shares or investment returns. Those belong to Phase 5.9 Financial & Participation Engine.

## Exact input references
A feasibility request must select:
- construction case
- construction scenario
- exact scenario cost snapshot
- exact urban regulation evaluation

The engine validates that all references belong to the same property/scenario context.

## Status
- `FEASIBLE`
- `CONDITIONAL`
- `NOT_FEASIBLE`
- `INSUFFICIENT_DATA`

Initial deterministic logic:
- regulation `NON_COMPLIANT` creates a blocking reason
- regulation `REVIEW_REQUIRED` creates a warning
- scenario not READY creates a warning
- missing land/built area reduces readiness
- invalid scenario cost is blocking

## Derived metrics
Where input data exists:
- cost per land square metre
- cost per built square metre
- FAR = total built area / land area
- proposed floor count
- readiness score from 0 to 100

The readiness score is an operational completeness/readiness indicator. It is not a financial return score and does not replace human engineering judgment.

## Input sources
Property:
- land area
- existing floors
- existing building area

Scenario assumptions may override/fill:
- `proposedFloors`
- `totalBuiltAreaM2`

Cost comes only from the selected Phase 5.6 `scenario_cost_snapshot`.

Regulatory status comes only from the selected Phase 5.7 `urban_evaluation`.

## Persistence
`feasibility_assessment` stores:
- exact source IDs
- status
- readiness score
- deterministic derived metrics
- input snapshot
- result snapshot
- engine version
- actor/time

`feasibility_reason` stores blockers, warnings and informational reasons with optional source-entity references.

## Lineage
case -> property
case/scenario -> feasibility assessment
scenario -> scenario cost snapshot
property/scenario -> urban evaluation

This phase prepares Phase 5.9 financial analysis and Phase 5.11 full analysis lineage/snapshot hardening.

## Deferred
- market sale value/revenue
- profit and ROI
- owner/builder participation shares
- financing/cash-flow schedule
- NPV/IRR
- sensitivity analysis
- AI recommendations
- frontend feasibility UI