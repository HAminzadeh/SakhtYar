# SakhtYar Context Engine

Java module independent of frontend/backend production code. SQLite FTS5 is the private persistence layer; the existing CodeGraph MCP remains the source of structural code relationships. Both MCP servers are registered side-by-side, never merged into a new graph database.

## APIs
- ContextStore: update source, search FTS, note/get, archive/search selected transcript, status.
- ProjectIndexer: bounded incremental source scan with secrets/path exclusions.
- SkillCatalog: curated local registry, status, install only reviewed adapted skills, conservative removal.
- CodeGraphBridge: read the `.mcp.json` entry and perform MCP initialize/tools-list diagnostic (on demand).
- ContextMcpServer: stdio JSON-RPC MCP transport for local memory.
- ContextCli: project setup, index, search, diagnostics, local installer.
- CcrUsageImporter: read-only aggregation from a user-selected JSON/JSONL export.

No hardcoded token cost. No Ollama, ECC Runtime, Serena, Mem0 or Claude-Mem required.
