# SakhtYar Hotfix — Chat / Skills

## Fixed
- Plain chat no longer launches in Plan mode and no longer exposes the large coding-tool surface. Plain chat now uses `--tools "" --disallowedTools "mcp__*" --disable-slash-commands`, with one turn per prompt. Project Mode enables read-only Read/Glob/Grep and keeps project MCPs.
- Chat errors are classified and summarized instead of displaying raw CCR JSON containing request/session details.
- Catalog draft skills can now be checked and installed **only after two separate confirmations**. They remain labeled `catalog` and are installed with `disable-model-invocation: true`; this DOES NOT make an incomplete draft a mature skill.
- Selected/installed skills are still resolved from `.claude/skills` at refresh and next startup. Existing managed copies remain protected by their signature.
- Unit test added for catalog install/reopen/delete.

## Important: CCR routing
The error in the screenshot is from CCR **local provider routing**, not from the Git-tracked Java sources:
`model_resolution|400: gpt-oss-120b not configured for target groq-free`.
In `http://127.0.0.1:3458` check **Agent Profiles → Claude-Free** and **Global Routing → Default on failure / Rule On failure**. Remove invalid `groq-free / gpt-oss-120b` fallback, or configure that model at the provider. A successful 1-token model check does not guarantee large Claude Code requests succeed. Local CCR database, secrets, and profiles are not changed by this hotfix.

## Installation
1. Close the JavaFX dashboard and finish any live Claude requests.
2. Run `Install-SakhtYar-Chat-Skills-Hotfix.ps1` from Downloads with `-ProjectRoot "D:\ChatGPT_Projects\SakhtYar\source"`.
3. It checks known SHA-256 baseline values, backs up overwritten files in `%LOCALAPPDATA%\SakhtYar\InstallerBackups`, runs Maven tests/build with existing dedicated proxy settings, and rolls back changed source files if Maven fails.
4. Run `cd D:\ChatGPT_Projects\SakhtYar\source` then `.\Run-Claude-Free.cmd`.
5. In Skills select a draft → Install → accept the separate draft warning → verify its marker and reopening state.
6. In Chat first use **Simple Chat** (read-only project mode unchecked). If CCR still fails, inspect CCR fallback locally, not JavaFX.

## Safety
No changes to your saved sessions, SQLite context, CodeGraph, API keys, `.mcp.json`, CCR databases, or Git branch. No provider credentials are read by the hotfix.

## Suggested next phase
- Replace catalog drafts with high-quality SakhtYar-specific adapted skills, with samples and tests.
- Add a safe CCR routing diagnostics view that reads config read-only and masks secrets.
- Add explicit model/provider selection with a live capability probe and retry-off diagnostic mode.
- Cache document snippets by content hash and cap attachment size, especially when resuming sessions.
- Measure actual input/response/cache and failure retries in CCR before/after plain chat mode; never promise a fixed percent.
