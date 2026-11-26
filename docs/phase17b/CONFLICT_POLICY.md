# Phase 17-B conflict and legal-confidence policy

- Duplicate equal numerical limits within overlapping scope are not a numerical conflict.
- Conflicting numerical limits of the same type and overlapping scope are **unresolved**; software does not claim the highest numeric priority is the superior legal authority.
- Country, province, city, district and property-type null/empty values represent broad scope only for conflict detection, never automatic legal authority.
- All ambiguous legal clauses, unreviewed exception metadata and textual conditions return REVIEW.
- `RegulationService` persists conflicting Rule IDs in `inputSnapshot.conflictRuleIds` and result `details.conflict`; conflict forces `REVIEW_REQUIRED` instead of COMPLIANT.
- Independent full legal scope certification is not implemented. A set of PASS rows **cannot** prove overall construction-plan compliance; pass-only result remains `REVIEW_REQUIRED`.
- Explicit FAIL without unresolved conflict may still yield `NON_COMPLIANT`.
- A legal specialist must review temporal precedence, special versus general provisions, annulment and editorial corrections; not inferred by numeric priority.
- Numeric comparisons use cross multiplication before division or display rounding.
- Material changes to a bound UrbanRule reset its verified binding and deactivate it; historical evaluation JSON snapshots are retained.
- Do not use these changes to issue building permits or make legal conclusions without source and jurisdiction verification.
