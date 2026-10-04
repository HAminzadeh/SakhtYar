# Knowledge Platform Control Plane

Migration: `V31__knowledge_platform_control_plane.sql`

APIs:
- GET `/api/v1/knowledge/admin/status`
- GET `/api/v1/knowledge/admin/profile`
- PUT `/api/v1/knowledge/admin/profile`
- POST `/api/v1/knowledge/admin/commands/generate`
- GET `/api/v1/knowledge/admin/runs`
- GET `/api/v1/knowledge/admin/runs/{runCode}/command`

This extends the existing knowledge module. Existing knowledge_source/term/alias/relation/candidate/revision, crawler and lineage structures remain part of the unified platform.

V31 adds control/governance schema for pipeline profiles/runs, datasets/artifacts, batch review, source policies, feedback, usage analytics, golden questions, quality runs, conflicts, document diff, impact analysis, pinning, search regression, agent snapshots and manual overrides.

The next prepared-dataset importer adds/finalizes document/evidence/chunk payload schema after real corpus extraction.