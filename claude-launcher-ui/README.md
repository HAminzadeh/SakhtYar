# SakhtYar Claude Control Center + Context Engine

Launch with `Run-Claude-Free.cmd` from IntelliJ Terminal in the SakhtYar source root. The CMD uses Maven only for compilation and runtime dependency copying, then directly executes `LauncherEntry` through `java.exe` so its child CCR CLI inherits the real IntelliJ terminal.

### New UI
- Home: CCR `Claude-Free` continue/new/choose, German proxy preflight, JavaFX remains open during same-console interactive CCR process.
- Skills: curated backend/frontend/architecture skills; adapted entries opt-in install after confirmation, catalog entries blocked until reviewed.
- Memory: SQLite/FTS5, CodeGraph MCP connection probe, indexing changed source files, local search and explicit Claude JSONL transcript import.
- Usage: imports a locally selected CCR JSON/JSONL log export; token counts only when present, no fabricated costs.
- Settings: default CodeGraph tool profile `core`, optional `ENABLE_TOOL_SEARCH=auto:5` opt-in (model-dependent), old connection/session options preserved.

Requires Java 17+, Maven, CCR installed, existing local provider profile `Claude-Free`, CodeGraph setup in `.mcp.json` and v2rayN HTTP/Mixed proxy.

Read `README_INSTALL_FA.md` at the project root of this release for upgrade instructions and limitations.
