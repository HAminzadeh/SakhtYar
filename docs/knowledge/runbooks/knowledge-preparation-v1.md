# SakhtYar Knowledge Preparation Pipeline v1

This stage extends V31/V32; it does not replace the existing Knowledge Dataset Package Import.

## Flow
Raw/deep-extracted documents -> normalization -> structure-aware nodes -> classification -> entities/references -> exact/near dedup -> evidence-grounded preparation candidates -> LLM input ZIP -> ChatGPT -> V32-compatible result ZIP -> validation/dry-run/import/review/publish.

## Identity and lineage
- workflow_id: end-to-end workflow
- run_id: one preparation execution
- correlation_id: stable lineage family across document versions
- source_document_id: immutable source version
- source_node_id: run-specific structural node

Raw large files remain on disk/object storage; PostgreSQL stores metadata, hashes, processing state, structure, candidates and lineage.

## Run
1. Start the backend once so Flyway applies V33, or run the normal backend migration workflow.
2. `powershell -ExecutionPolicy Bypass -File .\tools\knowledge-preparation\bootstrap-knowledge-preparation.ps1`
3. Set DB_URL, DB_USERNAME, DB_PASSWORD if defaults do not apply.
4. `powershell -ExecutionPolicy Bypass -File .\tools\knowledge-preparation\run-knowledge-preparation.ps1 -Stage All -InputRoot <deep-export-or-source-folder>`

The outbound ZIP is `SAKHTYAR_LLM_INPUT`; it is intentionally NOT a V32 import package. `command.txt` asks ChatGPT to return a V32-compatible dataset package while preserving lineage in provenance.