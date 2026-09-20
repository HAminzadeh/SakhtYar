# SakhtYar Phase 2 UI patch

Prerequisite: install the Phase 2 modular backend first.

Copy this patch over the repository root.

## Install

```powershell
cd D:\ChatGPT_Projects\SakhtYar\source\frontend
npm install
```

Create:

```text
frontend/.env.local
```

with:

```env
VITE_NESHAN_MAP_API_KEY=YOUR_NESHAN_WEB_MAP_KEY
```

Then:

```powershell
npm run build
npm run dev
```

Restart Vite after changing `.env.local`.

## Backend service integration

In the IntelliJ backend Run Configuration:

```text
NESHAN_ENABLED=true
NESHAN_SERVICE_API_KEY=YOUR_NESHAN_SERVICE_KEY
```

These are server-side values. Never put `NESHAN_SERVICE_API_KEY` in `VITE_*`.

## Smoke test

1. Open a Case.
2. Open the Map tab.
3. Confirm the Neshan map renders.
4. Search an address.
5. Select a result.
6. Drag the marker.
7. Confirm reverse-geocoded address appears.
8. Save location.
9. Refresh.
10. Confirm the marker returns to the same coordinates.
