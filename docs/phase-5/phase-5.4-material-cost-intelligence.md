# Phase 5.4 â€” Material & Cost Intelligence

## Purpose
Phase 5.4 introduces the canonical material catalog and evidence-based market price layer used by later cost, feasibility and participation engines.

## Core rules
- material identity is separate from observed market prices
- variants represent brand/model/grade/manufacturer-specific products
- every price observation has provenance through `knowledge_source`
- price observations are append-only evidence
- observations start as `PENDING_REVIEW`
- only `APPROVED` observations participate in aggregates
- derived aggregates can be rebuilt deterministically
- suspicious approved prices are flagged for anomaly review rather than silently removed
- AI/crawler/import data never becomes authoritative automatically

## Tables
- `material_item`
- `material_variant`
- `material_price_observation`
- `material_price_aggregate`
- `material_price_anomaly`

## Price aggregation
Aggregate version `1.0` groups approved observations by:
- material
- variant
- price type
- currency
- unit
- province
- city

Metrics:
- sample count
- minimum
- maximum
- average
- median
- period start/end

## Anomaly detection
The first deterministic rule compares a newly approved observation to the median of at least three prior approved observations in the same comparison group.

- deviation below 50%: not flagged
- deviation from 50% to below 100%: MEDIUM
- deviation of 100% or more: HIGH

This is a review signal, not an automatic rejection.

## Deferred intentionally
- unit conversion
- currency conversion
- time-series forecasting
- inflation normalization
- official construction price-index import adapters
- automated crawler-to-price extraction
- structural systems and quality-package composition
- assembly/BOM costing

Those belong to later stages.