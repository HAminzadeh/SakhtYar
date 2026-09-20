# SakhtYar Phase 2 - Modular Backend

This package is a complete replacement for the current `backend` directory,
based on the Phase 1 code plus the Phase 2 Geo/PostGIS/Neshan work.

## Important

Before replacing anything:

1. Commit or stash current work.
2. Keep the existing PostgreSQL/MinIO volumes.
3. Do **not** drop the database.
4. Do **not** edit V1/V2/V3 after they have been applied.

## Apply

From the repository root:

```powershell
Rename-Item backend backend-phase1-backup
```

Extract/copy the `backend` directory from this package into the repository.

The new main class is:

```text
backend/app/src/main/java/com/sakhtyar/SakhtYarApplication.java
```

In IntelliJ, reload Maven from:

```text
backend/pom.xml
```

Then update the Run Configuration classpath/module to `sakhtyar-app`.

## Build

```powershell
cd backend
mvn clean test
mvn -pl app -am package
```

## Run without Neshan first

The application can start with Neshan disabled:

```text
NESHAN_ENABLED=false
```

Start `SakhtYarApplication`. Flyway should apply:

```text
V4__property_postgis_location.sql
```

Existing Phase 0/1 data remains unchanged.

## Enable Neshan service APIs

Get a service API key from the Neshan Platform panel and add these to the
IntelliJ Run Configuration:

```text
NESHAN_ENABLED=true
NESHAN_SERVICE_API_KEY=<your service key>
```

Optional:

```text
NESHAN_BASE_URL=https://api.neshan.org
```

Then restart the backend.

Test:

```text
GET http://localhost:8080/api/v1/geo/status
```

After login, search/reverse endpoints are authenticated like the rest of the API.

## Phase 2 endpoints

```text
GET /api/v1/geo/status
GET /api/v1/geo/search
GET /api/v1/geo/reverse
GET /api/v1/geo/nearby-properties
```

## What modular means here

This is still **one application and one server process**. It is not a
microservice conversion. The benefit is isolated code ownership and dependency
boundaries without extra runtime cost.
