# Phase 5.11 â€” Data Lineage & Analysis Snapshot Hardening

## Purpose
Phase 5.11 turns the generic Phase 5.1 `analysis_snapshot` and `data_lineage` tables into an operational immutable analysis evidence layer.

It is intentionally generic so feasibility, financial, sensitivity and later engines can publish snapshots without introducing circular Maven dependencies.

## Snapshot model
A snapshot can identify:
- construction case
- optional construction scenario
- analysis type
- status
- schema version
- engine/knowledge/regulation/material-price versions
- optional root entity type + ID
- optional parent snapshot
- immutable input snapshot
- immutable result snapshot
- metadata
- source count
- SHA-256 content hash
- actor/time

## Content hash
`content_sha256` is calculated from a canonical JSON envelope containing:
- input snapshot
- result snapshot
- metadata

Map keys are deterministically ordered before hashing.

The hash is evidence of snapshot content identity. It is not a digital signature.

## Parent snapshots
`parent_snapshot_id` supports explicit chains such as:
- feasibility snapshot
- financial snapshot derived from feasibility
- sensitivity snapshot derived from financial

The service validates that a parent snapshot belongs to the same case.

## Generic lineage
Every new snapshot requires at least one lineage item.

A lineage record contains:
- output path
- relationship type
- source type
- source entity type / ID
- source URL / label
- observed timestamp
- source version
- source hash
- confidence
- metadata

Each lineage record must identify its source by at least one of:
- source entity ID
- source URL
- source label

## Why generic instead of direct finance/sensitivity dependencies?
The `finance` module already depends on `analysis`. Adding `finance` back into `analysis` would create a Maven dependency cycle.

Phase 5.11 therefore exposes a generic immutable snapshot API. Domain engines can publish their exact input/result payloads and source references without the analysis module importing those engines.

## Immutability
Phase 5.11 provides create/read APIs only.
No update/delete API is introduced for snapshots or lineage.

## Auditability
Snapshot creation emits an audit event including:
- case ID
- analysis type
- content SHA-256
- source count

## Deferred
- automatic publication hooks from every engine
- cryptographic signatures
- external timestamp authority
- content-addressed file storage
- snapshot export bundles
- frontend lineage graph
- lineage visualization