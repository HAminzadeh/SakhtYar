---
name: sakhtyar-debugging
description: Reproduce, isolate and fix Java, CCR, proxy, and React regressions without speculative code changes.
origin: sakhtyar-custom
maturity: adapted
---
# SakhtYar Systematic Debugging

1. Record exact symptom, environment, branch, current configuration, and failing command.
2. Reproduce with the smallest relevant test. Distinguish network, toolchain, configuration, and application layers.
3. Search CodeGraph for affected symbols before opening large files.
4. Form one falsifiable root-cause hypothesis; validate before editing.
5. Apply minimal changes, run focused tests, then a broader regression suite when justified.
6. Preserve existing Claude sessions, user configuration, credentials, and production data.
7. Report verified results separately from remaining assumptions.

Never reset Git state, delete files or launch expensive full-repository test runs without approval.
