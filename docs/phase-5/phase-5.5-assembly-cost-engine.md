# Phase 5.5 â€” Assembly & Cost Engine

## Purpose
Phase 5.5 converts reviewed material-market intelligence into reusable construction recipes and reproducible cost estimates.

An **assembly** represents one unit of construction output, for example:
- one square metre of masonry
- one square metre of floor finish
- one cubic metre of concrete work
- one metre of piping

Each assembly contains material components with quantity and waste assumptions.

## Core rules
- assembly codes are immutable
- components must reference canonical materials from Phase 5.4
- optional material variants can be pinned
- component units must match material base units in this phase
- waste is explicit and deterministic
- price selection uses approved/rebuilt `material_price_aggregate` records
- no raw or pending market observation is used directly
- the requested region/currency/price-type must match an available aggregate
- every calculated estimate stores estimate lines that reference the exact aggregate used
- historical estimates are not recalculated automatically when market prices change

## Price basis
Each component selects one aggregate statistic:
- `MEDIAN` (recommended default)
- `AVERAGE`
- `MIN`
- `MAX`

## Calculation
For each component:

`effectiveQuantity = componentQuantity Ã— (1 + wasteFactor) Ã— requestedAssemblyQuantity`

`lineTotal = effectiveQuantity Ã— selectedUnitPrice`

Estimate total is the sum of all line totals.

`unitCost = totalCost / requestedAssemblyQuantity`

## Tables
- `cost_assembly`
- `cost_assembly_component`
- `cost_estimate`
- `cost_estimate_line`

## Reproducibility
A cost line stores:
- material/variant
- selected aggregate ID
- base quantity
- waste factor
- effective quantity
- selected unit price
- line total
- price basis
- aggregate sample count
- aggregate source period

This prepares the platform for deeper lineage/snapshot work in Phase 5.11.

## Deferred intentionally
- labor and equipment cost catalogs
- nested/sub-assemblies
- automatic unit conversion
- currency conversion
- escalation/inflation factors
- quality-package composition
- project-wide takeoff/BOM
- scenario comparison
- UI management screens