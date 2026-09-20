# Phase 2 - Map UI

The case workspace now uses Neshan's MapLibre SDK for the interactive map.

## Browser-side key

Only the Neshan Web Map key is exposed to the frontend:

```text
VITE_NESHAN_MAP_API_KEY
```

The Neshan service key for Search/Reverse Geocoding must remain in the backend.

## Features

- Interactive Neshan map
- Existing Property marker
- Click-to-select location
- Draggable marker
- Browser geolocation
- Search through SakhtYar Backend
- Reverse Geocoding through SakhtYar Backend
- Save coordinates to Property
- Optional apply of Neshan reverse-geocoded address to Property

## API flow

```text
Browser
  |
  | map tiles: Web Map key
  v
Neshan Map SDK

Browser
  |
  | authenticated SakhtYar calls
  v
/api/v1/geo/*
  |
  | service key stays server-side
  v
Neshan REST API
```

This separation prevents the privileged service key from being shipped in the frontend bundle.
