# SakhtYar Phase 2 Geocoding Patch

Apply this package over the project root:

```text
D:\ChatGPT_Projects\SakhtYar\source
```

It updates both Backend and Frontend.

## Why this patch exists

The first Phase 2 package used Neshan `/v3/search`. The current Neshan service
panel available to the project exposes address geocoding instead, which is also
a better fit for SakhtYar because the user is locating a property address.

The flow is now:

```text
Property address
  -> Neshan address geocoding
  -> latitude/longitude
  -> map marker
  -> reverse geocoding
  -> Property + PostGIS
```

## Required Neshan service permissions

Enable these on the backend Service API key:

```text
تبدیل آدرس به نقطه
تبدیل نقطه به آدرس
```

Optional:

```text
تبدیل آدرس به نقطه پلاس
```

The Web Map key remains in:

```text
frontend/.env.local
VITE_NESHAN_MAP_API_KEY=...
```

The backend key remains in the local Spring configuration already created for
this project. Do not commit real keys.

## Build

Backend:

```powershell
cd D:\ChatGPT_Projects\SakhtYar\source\backend
mvn clean install
```

Frontend:

```powershell
cd D:\ChatGPT_Projects\SakhtYar\source\frontend
npm run build
npm run dev
```

## Smoke test

1. Open a Case -> نقشه.
2. Enter a property address in "جستجوی آدرس ملک".
3. Click جستجو.
4. Select the returned result.
5. The marker should move to the returned coordinates.
6. Reverse geocoding should show a Persian address.
7. Save with "ذخیره موقعیت + نشانی نشان".
8. Refresh the page and verify the marker remains at the saved location.
