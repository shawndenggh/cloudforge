# CloudForge Platform

CloudForge provides reusable platform capabilities for secure, tenant-aware distributed applications.

## Language

**Tenant**:
An organization or customer that forms an independent data and authorization boundary.
_Avoid_: Workspace, account, customer space

**Current Tenant**:
The single Tenant selected for the execution of one request or background operation. Permissions from different Tenants must never be combined in one execution context.
_Avoid_: Active workspace, selected account

**User**:
A platform-wide human identity with its own account lifecycle and one or more login methods. A User may be linked to Employees or External Contacts in multiple Tenants, but is not itself a Tenant identity or authorization relationship.
_Avoid_: Account, member, Employee, External Contact

**Session**:
Server-side browser authentication state for one User. A Session proves the global User identity but never proves Employee status, Tenant access, a Tenant Role, or business-resource permission.
_Avoid_: Tenant token, access authorization, Employee session

**Login Email**:
The globally unique, case-insensitive email-shaped identifier a User supplies for registration and password login. In the first release it is not verified and must not be treated as a trusted contact address or proof of email ownership.
_Avoid_: Verified email, Primary Email, permanent identity

**Primary Email**:
The one verified, globally unique email currently used to identify and contact a User. It may change after verification and may match an Employee's Work Email, but it is never the User's permanent identity.
_Avoid_: User ID, Work Email, permanent identity

**Password Credential**:
The one password verifier owned by a User for email-and-password authentication. It is security data separate from the User resource and is never exposed to another domain.
_Avoid_: User password field, reversible password, Session secret

**Employee**:
An internal person in one Tenant and that Tenant's organization directory. An Employee may exist before being linked to a User.
_Avoid_: Tenant user, member

**External Contact**:
An authenticated person outside a Tenant's organization who may collaborate on explicitly granted resources without becoming an Employee.
_Avoid_: External Employee, guest, external user

**Collaborator**:
The relationship that grants an Employee or External Contact access to one business resource.
_Avoid_: Employee, External Contact, Tenant Role

**OrgUnit**:
A single-parent node in a Tenant's organization tree.
_Avoid_: Tenant, Organization root

**OrgAssignment**:
An Employee's position in one OrgUnit. An Employee has one primary assignment and may have additional assignments.
_Avoid_: Department member

**Permission**:
A stable capability defined by the platform or an owning domain, such as viewing or refunding an order. Tenants may assign Permissions through roles but cannot invent new Permission codes.
_Avoid_: Authority code, access flag

**Tenant Role**:
A Tenant-scoped role assigned to Employees. MVP provides the built-in SUPER_ADMIN role, which has no authority outside its Tenant.
_Avoid_: Group, profile

**Platform Role**:
A platform-defined grouping of cross-tenant operational Permissions. Platform Roles are separate from Tenant Roles and cannot be created or granted by Tenant administrators; MVP provides the built-in PLATFORM_ADMIN role.
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
