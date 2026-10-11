# Tehran Golden Dataset — **draft; not yet gold**

The 12 questions in `tehran-golden-draft.json` are test questions referencing a real source filename in the repository. **No legal answer has been invented or certified.** The dataset deliberately includes missing-data, zoning, date and OCR abstention tests.

For each case, have an independent qualified Tehran planning/legal expert check the current official source and annotate:

- `expertVerified: true`
- `expertName`: nonempty reviewer identity
- `sourceSha256`: SHA-256 for the *exact* source edition reviewed
- `sourcePage`: source page, 1-based
- `expected`: `{ "value": 4, "operator": "<=", "unit": "FLOOR", "mustAbstain": false }` (ILLUSTRATIVE SHAPE ONLY; value 4 is **not** an asserted rule). For uncertainty use `mustAbstain: true` and null values.

Generate machine outputs in `predictions.json` with `{ "predictions": [{"id": "THR-G-001", "value": ..., "operator": ..., "unit": ..., "sourceSha256": ..., "sourcePage": ..., "abstained": false}] }`.

Run `python scripts/golden_evaluate.py --dataset <reviewed>.json --predictions predictions.json`. The evaluator refuses to compute scores unless there are expert-reviewed rows. It reports precision/recall and abstention accuracy, but **no thresholds are claimed as already achieved**.

The repository source PDF is not by itself evidence of current effective municipal rules: check superseding amendments, official authority, parcel zoning and local exceptions before production use.
