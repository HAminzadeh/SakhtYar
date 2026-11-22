# SakhtYar Context Engine — Java-first integration

## Scope
A standalone `claude-context-engine` Java 17+ module, designed for SakhtYar v2 (Java 21).
- Local project-private SQLite FTS5 database, incremental source index, named memory notes, and explicit Claude JSONL session import.
- Read-only CodeGraph MCP handshake/health check **without replacing or modifying the existing codegraph entry**.
- Project-local MCP memory server exposing `sakhtyar_memory_search`, `sakhtyar_memory_get`, `sakhtyar_memory_put`, `sakhtyar_memory_status` and `sakhtyar_session_search`.
- Local reviewed Skill catalog with version/maturity information, conservative installation into `.claude/skills/` and explicit confirmation. Unreviewed `catalog` drafts cannot be installed by the dashboard.
- Persian/English JavaFX Skills, Memory and Usage tabs. Claude remains interactive in the IntelliJ terminal with the JavaFX window open.
- Default CCR agent profile `Claude-Free`, proxy and German exit-IP checks preserved by your existing `LauncherCore`/`ProxyHealthService`.
- CodeGraph `core` tool profile default when recognized; tool search `auto:5` is opt-in due unknown `flint-v1.0:free` gateway compatibility.
- Read-only CCR usage **file import**; admin-token protected live CCR API is deliberately not scraped and paid costs are not fabricated.

## Overlay installation (do not replace your current LauncherCore.java)
1. Back up your `source` directory or create a Git branch/commit.
2. Extract the ZIP contents **into** `D:\\ChatGPT_Projects\\SakhtYar\\source`, preserving directory paths.
3. Files deliberately replaced: `Run-Claude-Free.cmd`, `claude-launcher-ui/pom.xml`, `claude-launcher-ui/src/main/java/ir/sakhtyar/launcher/Dashboard.java`.
4. Files deliberately **not** replaced: `LauncherCore.java`, `ProxyHealthService.java`, `AGENTS.md`, `.mcp.json`, provider keys, or any Claude conversation/history files.
5. New module files are in `claude-context-engine/`; new Java terminal worker is `TerminalCoordinator.java`; three custom skills are in `engineering/ai-harness/skills/`.
6. IntelliJ Terminal (PowerShell): `cd D:\\ChatGPT_Projects\\SakhtYar\\source` and `./Run-Claude-Free.cmd`.
7. The CMD bootstrap compiles/installs the independent Maven module locally, registers `sakhtyar-context` in `.mcp.json` preserving `codegraph`, then starts a **plain Java launcher process** with direct IntelliJ console handles. No business logic is in CMD.
8. In the Memory tab run **Index changed files** once. Indexing intentionally avoids source secrets and bulky build artifacts; future runs update only changed file digests.
9. In Skills select the groups and use **Install selected**, reviewing the confirmation. Adapted skills are suggested before launch; catalog drafts are excluded pending review.
10. Before Claude opens the existing CCR+Germany preflight runs. In the launcher choose Continue/New/Choose; the window stays open while CCR inherits terminal input/output.
11. Restart Claude sessions to pick up changes to `.mcp.json` and skills; earlier sessions preserve their history.

## Additional commands / verification
- `mvn -f claude-context-engine/pom.xml test`
- `mvn -f claude-launcher-ui/pom.xml clean compile`
- `java -cp "claude-context-engine/target/classes;claude-context-engine/target/dependency/*" ir.sakhtyar.context.ContextCli status .`
- `java -cp "claude-context-engine/target/classes;claude-context-engine/target/dependency/*" ir.sakhtyar.context.ContextCli graph .`
- Use `/mcp` in Claude; confirm **both** `codegraph` and `sakhtyar-context` appear and are connected.
- Use `/context all` before/after, controlling model, task, tools and cache. Compare token counts in CCR Logs where available.

## Important operational boundaries
- Not a magic reduction of Claude built-in System tools: those are provided by Claude Code and may remain loaded. CodeGraph MCP `core` cuts its own advertised tool surface only if the installed CodeGraph implementation supports it.
- The `ENABLE_TOOL_SEARCH=auto:5` switch can cause errors with free gateways; default OFF until tested against the actual CCR route. Disabling it does not reduce native tool definitions.
- Local JSONL transcript import is voluntary and private. No automatic copying of all chat files; data resides under `%LOCALAPPDATA%\\SakhtYar\\AI\\projects\\<id>\\context.db`.
- SQLite is not encrypted at rest. Protect the user's Windows account and local profile; do not commit or sync this DB.
- FTS5 gives keyword search, not remote-model embeddings. Avoid marketing it as semantic retrieval. CodeGraph continues to provide structural/semantic symbol search independently.
- MCP memory write is permitted only through Claude tool approval controls. Treat imported instructions as untrusted text; do not execute them as code.
- Tests with actual Windows IntelliJ + CCR, CodeGraph server subtype, v2rayN, and free OpenRouter model are still required. This bundle was NOT pushed or merged automatically.

## Known limitations / follow-up checks
- Actual Windows/IntelliJ console inheritance from the plain-Java launcher has not yet been verified on your machine. Maven is used only to build and resolve classpath, not to host Claude's console.
- CodeGraph tool profile support depends on the installed CodeGraph variant; its launcher registration is preserved and the health button probes its MCP handshake.
- The local store uses FTS5 keyword matching and an MCP query interface; embeddings / automatically generated LLM summaries are not included.
- Local transcript import stores user/assistant text only, without tool payloads, and only from an explicitly chosen file.
- Token-search opt-in is not asserted compatible with `OpenRouter/recursive/flint-v1.0:free` until a real request is tested.
- The existing CCR management page uses an authenticated local API. The current implementation imports an exported JSON/JSONL usage file, not an undocumented live CCR API.
- This archive overlays only the launcher and new module. It does not delete unrelated, old scripts; review them after running tests to avoid damaging existing developer workflows.

## Maven proxy repair
The self-contained installer writes Maven-Central-specific settings to %LOCALAPPDATA%\SakhtYar\Build, passing both -s and -gs to Maven. Global and user ~/.m2/settings.xml are not changed. Run-Claude-Free.cmd uses these same settings after installation. Default HTTP/Mixed proxy is 127.0.0.1:10808. Maven Central access is tested before overlay. Use -ProxyPort if needed or -DirectMaven only if direct access is explicitly desired.
