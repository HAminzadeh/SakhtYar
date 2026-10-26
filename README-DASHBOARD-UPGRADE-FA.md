# SakhtYar Claude Control Center — Dashboard Upgrade

## What changes
- New local JavaFX **Chat** tab: Persian RTL, text attachments, image/path references, response rendering, stop, new session; uses the existing CCR agent profile in noninteractive `claude -p --output-format json` mode and intentionally uses plan permissions (read-only workflows).
- **Skills** checkbox reflects file existence in `.claude/skills/` rather than the original source maturity. `Install checked skills` installs only checked *adapted* project skills. Separate **Uninstall unchecked managed skills** is explicit and checks SHA256 marker before deletion. A new Programming category uses already tracked SakhtYar catalog skill files. Unreviewed `catalog` items are disabled.
- **Usage** charts from either a compatible local CCR SQLite usage table or a user-selected real CCR JSON/JSONL export (read-only; no remote API; schema mismatch is reported, never invented).
- **Providers** tab reads provider names/model metadata from CCR's Windows config directory and displays configured profiles. A live test uses a selected **CCR agent profile**, not an individual raw upstream provider. CCR's own admin UI remains the authority for checking a specific raw provider/model; no private API tokens exposed.
- **Settings**: short system/tool descriptions through `CLAUDE_CODE_SIMPLE_SYSTEM_PROMPT=1`. Tool Search remains opt-in. It can reduce prompt overhead but does NOT remove native tools, and savings for Flint/CCR are not guaranteed.

## Installation

Run the self-contained PowerShell installer from Downloads:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Downloads\Install-SakhtYar-Dashboard-Upgrade.ps1" -ProjectRoot "D:\ChatGPT_Projects\SakhtYar\source"
```

The installer verifies SHA256, backs up the changed files to `%LOCALAPPDATA%\SakhtYar\InstallerBackups`, builds the context module then the JavaFX launcher using existing local Maven settings, and rolls back its own file overlay if build fails. It does NOT delete any prior Claude sessions, existing CodeGraph or CCR settings. No Git commit/push.

After successful installation, from the IntelliJ terminal:

```powershell
cd D:\ChatGPT_Projects\SakhtYar\source
.\Run-Claude-Free.cmd
```

## Caveats / outstanding validation
- The JavaFX/CCR runtime requires a real Windows/IntelliJ acceptance test; only the pure Java SkillCatalog compiled in the authoring environment (no Maven/JavaFX available there).
- Image-only models cannot inspect image attachments directly; current `recursive/flint-v1.0:free` has no assured vision support. To analyze an image, switch to a vision-capable profile and test its support.
- The chat print path uses a separate Claude session identifier. It does not mirror the live interactive terminal's current in-memory state.
- The upstream CCR schema varies by version. If the provider inventory cannot be obtained safely from config.sqlite or gateway.config.json, open CCR administration using the provided button.
- The dashboard does not directly lower the 33k System Tools shown in `/context`; the short-descriptions experiment must be measured before/after with the same model and tools.
- Raw JSON model response and actual provider auth stay inside CCR/Claude. No API keys are exported by these tabs.
