# Minimal initial service topology

CloudForge initially deploys `iam` as its only domain bounded context and `gateway` as a non-domain edge application. Identity, credentials, Tenants, Memberships, roles, and permissions remain internal IAM modules rather than separate remote services. New deployable domain services are introduced only when a concrete business capability, independent data ownership, and independent change cadence justify the network seam; speculative audit, notification, tenant, and user services are deferred.
