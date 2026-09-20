# Phase 2 - Modular Backend and Geo

Phase 2 converts the backend into a Maven multi-module modular monolith while
keeping one deployable Spring Boot application.

## Why modular monolith

SakhtYar needs strict domain boundaries, but it does not need the operational
cost of microservices at this stage. Each bounded context is therefore a Maven
module. Maven compile dependencies enforce many boundaries while the `app`
module assembles a single executable JAR.

## Module layout

```text
backend/
├── pom.xml
├── app/
└── modules/
    ├── shared-kernel/
    ├── audit/
    ├── identity/
    ├── casefile/
    ├── property/
    ├── owner/
    ├── document/
    ├── geo/
    └── integration-neshan/
```

## Dependency direction

```text
shared-kernel

audit
  ↑
identity
casefile
  ↑
property
  ↑
owner

casefile + audit
  ↑
document

geo
  ↑
integration-neshan

all modules
  ↑
app
```

The Neshan implementation is not referenced directly by the Geo domain.
`geo` defines `GeoProvider`; `integration-neshan` implements that port.

This lets us replace Neshan later or add another provider without changing
the Geo API/application layer.

## PostGIS

Migration V4 adds:

```sql
location geography(Point, 4326)
```

to `property`, a GiST index, and a database trigger that keeps `location`
synchronized with `latitude` and `longitude`.

The application still stores latitude/longitude because they are convenient
for APIs and UI. PostGIS becomes the source used for spatial queries.

## Geo API

```text
GET /api/v1/geo/status
GET /api/v1/geo/search?term=...&lat=...&lng=...
GET /api/v1/geo/reverse?lat=...&lng=...
GET /api/v1/geo/nearby-properties?lat=...&lng=...&radiusMeters=1000
```

## Neshan configuration

The service/API key is server-side only:

```text
NESHAN_ENABLED=true
NESHAN_SERVICE_API_KEY=...
NESHAN_BASE_URL=https://api.neshan.org
```

The browser map key is separate and belongs to the frontend.

## Existing Flyway history

V1, V2, and V3 must not be modified after they have been applied. Their
contents in this package are preserved from the current repository so Flyway
checksums remain valid. Phase 2 changes start at V4.
