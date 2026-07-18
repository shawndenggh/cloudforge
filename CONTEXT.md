# CloudForge Platform

CloudForge provides reusable platform capabilities for secure, tenant-aware distributed applications.

## Language

**Tenant**:
An organization or customer that forms an independent data and authorization boundary.
_Avoid_: Workspace, account, customer space

**Tenant Membership**:
The relationship that allows one User to participate in one Tenant with tenant-specific roles. A User may have memberships in multiple Tenants.
_Avoid_: Tenant user, user tenant

**Current Tenant**:
The single Tenant selected for the execution of one request or background operation. Permissions from different Tenants must never be combined in one execution context.
_Avoid_: Active workspace, selected account

**User**:
A human identity that may hold Tenant Memberships in one or more Tenants.
_Avoid_: Account, member

**Permission**:
A stable capability defined by the platform or an owning domain, such as viewing or refunding an order. Tenants may assign Permissions through roles but cannot invent new Permission codes.
_Avoid_: Authority code, access flag

**Tenant Role**:
A Tenant-defined grouping of Permissions assigned to Tenant Memberships. A Tenant Role has no authority outside its Tenant.
_Avoid_: Group, profile

**Platform Role**:
A platform-defined grouping of cross-tenant operational Permissions. Platform Roles are separate from Tenant Roles and cannot be created or granted by Tenant administrators.
_Avoid_: Super tenant role, global tenant role

**Integration Event**:
A versioned fact published by one bounded context for consumption by other bounded contexts. Its contract is part of the producer's Published Language and must not expose domain entities or persistence models.
_Avoid_: Shared entity, remote domain object

**Integration Contract**:
The single shared representation of an Integration Event used by both producers and consumers. It contains transport-only data and compatibility rules, never domain behavior, JPA mappings, repositories, or aggregate types.
_Avoid_: Common domain model, shared entity library

**Event Envelope**:
The platform-defined metadata wrapper around an Integration Event payload. It identifies the event, contract version, source, scope, Current Tenant, time, correlation, and causation independently of business data.
_Avoid_: Event payload header, business event base class

**Ordered Subscription**:
A consumer subscription whose business correctness depends on processing one subject's Integration Events in subject-version order. It uses single-active consumption and stops on gaps or unrecoverable failures instead of skipping ahead.
_Avoid_: Ordered event, globally ordered bus
