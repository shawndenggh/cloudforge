# Module map

## Deployable applications

### `services/iam`

The only initial domain bounded context. Its internal package seams are:

- `identity`: Users, credentials, authentication policy, and account lifecycle.
- `tenancy`: Tenants, Memberships, and Current Tenant selection.
- `authorization`: Permission definitions, Tenant Roles, Platform Roles, and permission expansion.
- `protocol`: OAuth 2.1 and OpenID Connect adapters.

These packages share one consistency boundary and database. They are not remote services.

### `services/gateway`

A thin Spring Cloud Gateway Server MVC application for routing and edge policies. It owns no domain data and performs no domain authorization.

## Shared modules

### `shared/security`

Provides the tenant-aware security seam consumed by future domain services: stable permission conversion and a `CurrentTenant` interface backed by the authenticated JWT. Each domain service still owns its `SecurityFilterChain` and endpoint authorization rules.

### `shared/messaging`

Provides the versioned `EventEnvelope` and its tenant and ordering invariants. RabbitMQ topology, Outbox, and Inbox implementations will be added behind this seam when the first producer-consumer workflow is implemented.

## Dependency rules

- Deployable applications may depend on shared modules.
- Shared modules never depend on deployable applications.
- One deployable application never imports another application's implementation.
- Remote interaction uses HTTP contracts or Integration Events, not shared JPA entities.
- A new service requires a concrete business capability, owned data, and an independently useful deployment boundary.

