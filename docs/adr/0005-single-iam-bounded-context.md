# Single IAM bounded context

CloudForge provides one `iam-service` bounded context for login identities, credentials, Tenants, Tenant Memberships, role and permission assignments, credential issuance, and security audit. It does not introduce overlapping `user-service` and `identity-service` deployments, and business domains reference IAM identities by stable IDs rather than sharing IAM entities or reading its database.
