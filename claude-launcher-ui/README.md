# SakhtYar Claude Control Center v4

Separate JavaFX module. Requires Windows, Java 17+, Maven, v2rayN with working HTTP/Mixed proxy, CCR CLI and an enabled Agent Config profile named `Claude-Free`.

- Home: Continue project (CCR profile `Claude-Free` with Claude `--continue`), new conversation, or choose previous conversation (`--resume`).
- Sessions: history is not deleted; Claude Code stores history under the current project path.
- Usage: shows **unavailable** rather than fabricating token costs. CCR management UI can provide real usage data based on configured provider.
- Settings: categories collapsed by default; existing feature checkbox JSON preserved; extra profile names stored in `_claude_launcher/provider-profiles.json`.
- New providers: first configure and enable their matching Agent Config profiles within CCR, then add the exact profile name to this UI. Paid billing belongs to the provider, not this launcher.
- No TUN, no guaranteed leak prevention or Windows firewall rules.

## Limitations
Some legacy feature checkboxes are still configuration toggles, not fully implemented behavior. CCR launch is detached into a dedicated console, so the dashboard does not supervise that interactive terminal after startup. Avoid claiming live kill-guard protection from this version. The `Usage` tab intentionally does not display synthetic usage or infer prices. CCR argument syntax depends on installed version, consult `ccr --help` when needed.

## Upgrade
Put `Start-SakhtYar-v4.ps1` in project root and execute it; the embedded archive installs/backs up changed module files. Existing `_claude_launcher/launcher-settings.json` is retained. `-InstallOnly` extracts without starting JavaFX.
