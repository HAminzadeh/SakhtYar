# Navigation R01 Handoff

1. After ApplySource, run Verify with Node.js >=24, npm dependencies installed and clean local repo except package files.
2. Visit `/cases` and check desktop grouping at 1440px, collapsed Drawer at 1024px and 375px.
3. Open a case, switch each of four stages and verify all twelve panels load; test assistant with and without `AGENT_USE`.
4. Check `/cases/<id>?section=urban-analysis` deep link, Refresh, Back and unknown section fallback.
5. Use separate roles to verify menu items are hidden when permissions are missing; Backend remains authoritative.
6. Check dark theme and translated/RTL layout. Fix any browser findings before a production release.
7. Do not confuse the four navigation stages with legal/commercial project status or sign-off.
