# SakhtYar Claude Launcher (Java-first)

All core logic belongs to Java (`LauncherCore`); the JavaFX `Dashboard` is the presentation layer. `Run-Claude-Free.cmd` is the minimal bootstrap and terminal hand-off required to start the interactive CCR CLI in the same IntelliJ Terminal. No PowerShell business scripts.

Run from IntelliJ Terminal at the repository root: `Run-Claude-Free.cmd` (PowerShell: `.\Run-Claude-Free.cmd`). Requires JDK 17+, Maven, `ccr.cmd`, v2rayN HTTP/Mixed proxy, CCR profile `Claude-Free`.

The UI supports Persian RTL (default) and English LTR, keeps project sessions (`--continue`, `--resume`) and stores preferences under `_claude_launcher/control-center-settings.json`. CCR is started via Java if not already listening on port 3456. Germany proxy check uses ipwho.is via HTTP proxy port 10808. Port overrides: `SAKHTYAR_PROXY_PORT` and `SAKHTYAR_CCR_PORT`.

No TUN, no firewall leak prevention. Usage is only shown when trustworthy data exists. Enabling feature settings does not imply every advanced feature is implemented. The router and network checks run on a worker thread. The CMD shim launches CCR after JavaFX exits, preserving IntelliJ terminal input.
