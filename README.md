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

The repository includes an [SDKMAN environment](.sdkmanrc) pinned to Temurin Java 25. Developers with SDKMAN installed can install and activate it from the repository root:

```bash
sdk env install
sdk env
```

SDKMAN can also switch environments automatically when `sdkman_auto_env=true` is enabled in the developer's local SDKMAN configuration. The Gradle Wrapper remains able to download a matching Java 25 toolchain when necessary. Apply and verify the complete quality policy with:

```bash
make format
make check
```

`make check` is the merge gate: it verifies repository and Java formatting, the Apache 2.0 source header, Spring Checkstyle, Error Prone, NullAway/JSpecify, ArchUnit boundaries, and tests. See the [code style and quality gate](docs/development/code-style.md) and [ADR 0030](docs/adr/0030-executable-spring-code-quality-gate.md).

Useful module tasks:

```bash
./gradlew :services:gateway:test
./gradlew :services:iam:test
./gradlew :services:gateway:bootRun
./gradlew :services:iam:bootRun
```

GitHub Actions checks Gateway and IAM as separate jobs, with `fail-fast` disabled so one service failure does not prevent the other service from completing. Repository style and the shared modules run in independent jobs. See [CI Check](.github/workflows/ci-test.yml).

## Quick start

Initialize the developer environment and start the complete local stack with one command:

```bash
make up
```

This creates `.env` when missing, installs and activates the `.sdkmanrc` JDK when SDKMAN is available, waits for PostgreSQL, RabbitMQ, and Redis to become healthy, then runs IAM and Gateway together. Press `Ctrl+C` to stop the applications; the data services remain available for the next run.

The same lifecycle can be run in separate terminals when debugging:

```bash
make init       # Prepare .env, Java, and Gradle
make infra-up    # Start and health-check data services
make run         # Run IAM and Gateway
make status      # Show data service status
make down        # Stop data services, preserving volumes
```

Run `make help` to list all development commands. Ports can be overridden without editing committed files, for example `make up IAM_SERVER_PORT=19000 GATEWAY_SERVER_PORT=18080`.

CloudForge is open source under the [Apache License 2.0](LICENSE).

## Local infrastructure

Local application development uses Docker Compose rather than requiring Kubernetes. The underlying manual commands remain available when Make is not installed:

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
