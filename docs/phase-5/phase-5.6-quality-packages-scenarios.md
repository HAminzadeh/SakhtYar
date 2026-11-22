# Phase 5.6 â€” Quality Packages & Construction Scenarios

Phase 5.6 adds reusable quality packages and project construction scenarios on top of Phase 5.5 assemblies.

Quality levels:
- ECONOMY
- STANDARD
- PREMIUM
- LUXURY
- CUSTOM

Quality packages contain named slots pointing to canonical assemblies. Construction scenarios reference an optional package, structural system, assumptions and explicit assembly quantities.

Scenario item sources:
- PACKAGE
- OVERRIDE
- MANUAL

Scenario lifecycle:
- DRAFT
- READY
- ARCHIVED

A READY scenario must be active and contain at least one enabled item.

Scenario costing reuses the Phase 5.5 Assembly & Cost Engine. Every scenario cost snapshot stores the exact assembly estimate IDs used, preserving the lineage:

scenario -> scenario item -> assembly estimate -> estimate line -> material price aggregate.

This phase does not add frontend/UI screens.

Deferred:
- automatic quantity takeoff
- labor/equipment/subcontractor pricing
- package auto-expansion into project quantities
- currency/inflation conversion
- urban/regulation validation
- feasibility and sensitivity analysis