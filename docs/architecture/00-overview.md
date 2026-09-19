# SakhtYar Architecture V02

مرجع معماری محصول «طراحی جامع مشارکت ساخت 02» است.

## Foundation

```text
React / TypeScript
        |
      REST
        |
Java / Spring Boot modular monolith
        |
+-------+---------+----------+
|                 |          |
PostgreSQL     Redis      MinIO
PostGIS
pgvector
```

## Core rules

1. Modular monolith first; split heavy workers only when required.
2. Persian Agent is the language gateway for future agent workflows.
3. Agent communication uses versioned JSON schemas.
4. Financial, urban-rule and feasibility math stays deterministic in Java.
5. Sensitive financial/legal actions require human approval.
6. External providers are accessed through adapters.
7. Source provenance and auditability are mandatory.
