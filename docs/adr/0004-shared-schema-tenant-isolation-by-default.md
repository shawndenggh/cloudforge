# Shared-schema tenant isolation by default

Each CloudForge domain service owns its logical database, while tenants initially share that service's tables. Tenant-owned rows carry `tenant_id`, application data access is tenant-scoped, and PostgreSQL row-level security provides defense in depth; physical database-per-tenant isolation is deferred until a concrete compliance, contractual, or scale requirement justifies its operational cost.
