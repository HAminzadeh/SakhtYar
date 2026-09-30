# Phase 5.10 â€” Sensitivity Analysis

## Purpose
Phase 5.10 measures how project financial outputs change when selected assumptions move around an exact Phase 5.9 financial baseline.

The engine is deterministic and does not forecast or invent future market values.

## Baseline
Every sensitivity analysis references one immutable `financial_analysis`.

Baseline values reused:
- sellable area
- expected sale price per mÂ²
- base construction cost
- other revenue
- additional cost
- financing cost
- taxes and fees
- case/scenario/currency context

## Supported shocks
Each point independently specifies:
- `salePriceChangePercent`
- `sellableAreaChangePercent`
- `constructionCostChangePercent`

A zero/zero/zero point reproduces baseline economics.

The API accepts up to 250 explicit points. This avoids hidden combinatorial expansion and keeps every tested scenario visible and reproducible.

## Calculated outputs per point
- adjusted sellable area
- adjusted sale price per mÂ²
- adjusted base construction cost
- gross revenue
- total project cost
- projected profit
- ROI
- profit margin

Unchanged costs (additional, financing, taxes/fees, other revenue) remain fixed from the baseline.

## Summary
The analysis stores:
- best/worst projected profit
- best/worst ROI
- point count
- algorithm version
- baseline snapshot
- summary snapshot
- actor/time

## Boundaries
Sensitivity analysis is a deterministic what-if tool, not a probabilistic forecast.
Phase 5.10 does not assign probabilities to scenarios and does not recommend an investment decision.

## Deferred
- Monte Carlo simulation
- probability distributions
- correlated variables
- cash-flow sensitivity
- NPV/IRR sensitivity
- tornado charts/UI
- automatic scenario generation
- AI recommendations