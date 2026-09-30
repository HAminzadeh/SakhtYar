# Phase 5.9 â€” Financial & Participation Engine

## Purpose
Phase 5.9 adds deterministic project financial analysis and flexible participation allocation on top of an exact Phase 5.8 feasibility assessment.

The engine does not infer market values or contractual shares. Sale price, sellable area, additional costs and participant shares are explicit inputs.

## Financial inputs
A calculation requires:
- exact `feasibilityAssessmentId`
- sellable area
- expected sale price per mÂ²
- currency
- other revenue
- additional cost
- financing cost
- taxes and fees
- participant allocation plan

The base construction cost is inherited from the selected feasibility assessment and therefore from the exact scenario cost snapshot used by Phase 5.8.

## Core formulas
- gross revenue = sellable area Ã— expected sale price per mÂ² + other revenue
- total project cost = base construction cost + additional cost + financing cost + taxes/fees
- projected profit = gross revenue - total project cost
- ROI % = projected profit / total project cost Ã— 100
- profit margin % = projected profit / gross revenue Ã— 100

ROI is null when total cost is zero.
Profit margin is null when gross revenue is zero.

## Participation model
Participants can be:
- OWNER
- BUILDER
- INVESTOR
- OTHER

Each participant has two independent percentages:
- `valueSharePercent`: share of gross project value/revenue
- `costSharePercent`: share of project cost responsibility

Both sets of shares must independently total 100%.

This supports common participation arrangements where owners receive a value share while builders carry most or all construction cost.

## Entity validation
For OWNER participants:
- `participantRefId` is required
- it must reference an owner belonging to the selected case property

For BUILDER participants:
- `participantRefId` is required
- it must reference a `project_builder` belonging to the selected case

INVESTOR and OTHER participants may be represented without an internal entity reference.

## Participant outputs
For each participant:
- allocated revenue/value
- allocated cost responsibility
- projected net value = allocated revenue - allocated cost

These values are deterministic projections, not contractual settlement records.

## Reproducibility
Every financial analysis stores:
- exact feasibility assessment ID
- case/property/scenario IDs
- all financial assumptions
- calculation outputs
- algorithm version
- actor/time
- participant allocations

## Boundary
This phase is a planning and analysis layer. It does not replace legal, tax, accounting or investment advice, and does not execute contractual distributions.

## Deferred
- scheduled project cash flows
- NPV / IRR
- payment milestones
- inflation/escalation
- debt amortization
- taxes by jurisdiction
- unit-by-unit physical allocation
- contract synchronization
- sensitivity analysis (Phase 5.10)
- frontend financial UI