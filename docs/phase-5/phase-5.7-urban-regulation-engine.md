# Phase 5.7 â€” Urban & Regulation Engine

## Purpose
Phase 5.7 introduces a deterministic, source-traceable rule engine for urban and municipal constraints.

Rules are explicit data records. They are not invented by AI and must reference a `knowledge_source`.

## Supported initial rule types
- `MAX_FLOORS`
- `MAX_FAR`
- `MAX_COVERAGE_PERCENT`
- `MIN_PASSAGE_WIDTH`
- `MIN_FRONTAGE`
- `MAX_HEIGHT`

Each rule has:
- immutable code
- Persian/English name
- jurisdiction scope
- optional property type
- source/provenance
- validity dates
- priority
- JSON parameters
- active state

## Jurisdiction matching
A blank rule scope behaves as a wildcard.

Rules can be scoped by:
- province
- city
- district
- property type

## Evaluation inputs
Property facts come from the Property module.

When a construction scenario is supplied, the following optional scenario assumptions are recognized:
- `proposedFloors`
- `totalBuiltAreaM2`
- `footprintAreaM2`
- `proposedHeightM`

For backward compatibility, if `proposedFloors` or `totalBuiltAreaM2` is absent, the engine can use existing property floors/building area.

The engine never guesses missing frontage, passage width, footprint, height, or other required inputs. Missing facts produce `REVIEW`.

## Outcomes
Rule outcomes:
- `PASS`
- `FAIL`
- `REVIEW`

Evaluation status:
- any FAIL -> `NON_COMPLIANT`
- no FAIL but at least one REVIEW -> `REVIEW_REQUIRED`
- all PASS -> `COMPLIANT`

## Lineage
Every evaluation stores:
- property/scenario IDs
- deterministic input snapshot
- rule results
- source IDs through the rule
- algorithm version
- actor/time

This creates a reproducible regulation layer for Phase 5.8 feasibility analysis.

## Important boundary
This engine is a decision-support layer, not an official municipal permit determination.
Only reviewed rule data from authoritative or appropriately trusted sources should be treated as operationally reliable.

## Deferred
- automatic GIS/zoning polygon lookup
- municipality-specific expression language
- parking rules
- setbacks
- density incentives
- heritage/fire/environmental constraints
- automatic permit workflows
- regulation UI