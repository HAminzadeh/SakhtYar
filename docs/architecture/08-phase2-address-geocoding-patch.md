# Phase 2 - Address Geocoding Patch

This patch replaces the first Phase 2 Neshan place-search integration with
address geocoding, matching the services exposed in the current Neshan panel.

## Neshan services used

- Web Map key: frontend map rendering
- Service key: "تبدیل آدرس به نقطه" (address -> coordinates)
- Service key: "تبدیل نقطه به آدرس" (coordinates -> address)

Optional for later work:

- "تبدیل آدرس به نقطه پلاس" for plaque/POI-oriented resolution

Static-map services are not required by this UI.

## Backend API

Preferred endpoint:

```text
GET /api/v1/geo/geocode?address=تهران...
```

Reverse geocoding remains:

```text
GET /api/v1/geo/reverse?lat=35.7&lng=51.4
```

The old `/api/v1/geo/search` route is preserved as a compatibility alias, but
internally delegates to address geocoding and no longer calls `/v3/search`.

## Neshan upstream endpoints

The adapter uses:

```text
GET https://api.neshan.org/geocoding/v1?json={"address":"..."}
GET https://api.neshan.org/v5/reverse?lat=...&lng=...
```

Both requests send the service key through the `Api-Key` request header.
