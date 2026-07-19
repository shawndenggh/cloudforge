# Module map

## Deployable applications

### `services/iam`

Owns global identity and Tenant lifecycle. Its internal package seams are:

- `identity`: Users, credentials, authentication policy, and account lifecycle.
- `tenancy`: Tenants and their lifecycle; it does not own Employee or External Contact.
- `authorization`: Platform Roles only; Tenant business authorization stays in the owning domain application.
- `protocol`: Login and Session adapters.

These packages share one consistency boundary and database. They are not remote services. See the [user and identity technical design](user-identity-technical-design.md).

### `services/gateway`

A Spring Cloud Gateway Server MVC BFF for the single MVP browser host. It validates the Host-only Redis Session, establishes a trusted User context, enforces CSRF, strips client-supplied identity headers, and routes whitelisted user interfaces. It owns no domain data and performs no Employee or business-resource authorization.

### `services/organization`

Owns Tenant-local OrgUnits, Employees, OrgAssignments, External Contacts, Tenant Roles, directory search, imports, and organization audit. It uses the request Tenant and trusted Session User to enforce Employee status and its own interface permissions locally. It does not own Tenant, User credentials, or business-resource Collaborators. See the [technical design](organization-service-technical-design.md).

## Shared modules

### `shared/security`

Provides the security seam for the versioned Session User context, trusted internal User context, explicit Current Tenant path context, and uniform 401/403 handling. It depends only on Spring Security libraries and provides no Spring Boot auto-configuration. Each domain application owns bean wiring, its `SecurityFilterChain`, and interface authorization rules.

### `shared/messaging`

Provides the versioned `EventEnvelope` and its tenant and ordering invariants. RabbitMQ topology, Outbox, and Inbox implementations will be added behind this seam when the first producer-consumer workflow is implemented.

## Dependency rules

- Deployable applications may depend on shared modules.
- Shared modules never depend on deployable applications.
- Shared modules never depend directly on Spring Boot, including its Starters and auto-configuration packages; they use lower-level framework libraries managed by the platform BOM.
- One deployable application never imports another application's implementation.
- Remote interaction uses HTTP contracts or Integration Events, not shared JPA entities.
- A new service requires a concrete business capability, owned data, and an independently useful deployment boundary.
