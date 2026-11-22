# SakhtYar - Graph First and Token Efficiency

## Code navigation
- Prefer CodeGraph MCP for architecture, dependencies and symbol discovery.
- Query specific classes, methods, or modules.
- Avoid repository-wide exploration unless explicitly requested.
- Reuse code returned by CodeGraph instead of reading it again.

## Token efficiency
- Keep CodeGraph queries narrow.
- Avoid unnecessary Read, Grep and Glob calls.
- Do not repeatedly fetch the same code.
- Keep responses concise and focused.
- Avoid verbose command output and unnecessary tool calls.

## Safe development
- Before edits, inspect relevant dependencies using CodeGraph.
- Read exact current source when necessary for safe changes.
- Change only files relevant to the requested task.
- Run focused tests before broader test suites.
- Never expose API keys or secrets.
- Never modify unrelated code.

## Reporting
- Distinguish verified findings from assumptions.
- Give concise summaries with relevant file paths.
- Report only important errors and next actions.
