# CloudForge

CloudForge is a production-oriented, multi-tenant foundation for Spring-based distributed applications. The repository is a Gradle Groovy multi-project build and currently contains the smallest service topology justified by the confirmed domain model.

## Platform baseline

- Java 25
- Spring Boot 4.1.0
- Spring Cloud 2025.1.2 (Oakwood)
- Gradle 9.6.1
- PostgreSQL, RabbitMQ, and Redis
- Kubernetes-only production deployment

## Modules

```text
services/
├── gateway/       Thin Spring Cloud Gateway Server MVC edge application
└── iam/           IAM bounded context and OAuth/OIDC authorization server

shared/
├── security/      Tenant-aware Resource Server integration
└── messaging/     Versioned Integration Event envelope
```

`iam` is currently the only domain service. User identity, credentials, Tenants, Memberships, roles, and permissions are internal IAM modules rather than independent network services. `gateway` is a deployable edge application, not a bounded context. See [the module map](docs/architecture/modules.md) and [ADR 0029](docs/adr/0029-minimal-initial-service-topology.md).

## Build

The Gradle Wrapper downloads Gradle and a matching Java 25 toolchain when necessary:

```bash
./gradlew test
./gradlew build
```

Useful module tasks:

```bash
./gradlew :services:gateway:bootRun
./gradlew :services:iam:bootRun
```

## Local infrastructure

Local application development uses Docker Compose rather than requiring Kubernetes:

```bash
cp .env.example .env
# Replace the placeholder passwords in .env, then export them for local applications.
set -a
source .env
set +a
docker compose up -d
```

The default local endpoints are:

- PostgreSQL: `localhost:5432`, database `iam`
- RabbitMQ: `localhost:5672`, management UI `http://localhost:15672`
- Redis: `localhost:6379`
- IAM: `http://localhost:9000`
- Gateway: `http://localhost:8080`

The IAM module currently establishes its runtime dependencies and internal package boundaries; user persistence, registered OIDC clients, signing keys, and production login behavior will be added as tested domain slices rather than insecure placeholder implementations.

## Architecture decisions

Confirmed decisions live under [`docs/adr`](docs/adr), and the multi-tenant ubiquitous language lives in [`CONTEXT.md`](CONTEXT.md). New services must own a concrete business capability and data model; infrastructure reuse alone is not a reason to create a network boundary.
