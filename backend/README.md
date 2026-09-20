# SakhtYar Backend

The backend is a Maven multi-module **modular monolith**.

It is intentionally not split into microservices. The modules compile independently
and enforce dependency boundaries, while `app` assembles them into one Spring Boot
runtime and one deployable JAR.

## Modules

```text
app
├── shared-kernel
├── audit
├── identity
├── casefile
├── property
├── owner
├── document
├── geo
└── integration-neshan
```

## Build

```bash
mvn clean verify
```

Build only the executable application and its dependencies:

```bash
mvn -pl app -am clean package
```

## Run in IntelliJ

Run:

```text
backend/app/src/main/java/com/sakhtyar/SakhtYarApplication.java
```

Use:

```text
JRE: Java 21
Classpath/module: sakhtyar-app
```

## Module rule

A domain module may depend only on modules explicitly declared in its Maven POM.
External systems are implemented behind domain ports. For example, `geo` defines
`GeoProvider`, and `integration-neshan` implements it.
