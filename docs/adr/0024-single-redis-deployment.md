# Single Redis deployment

CloudForge initially uses one logical Redis deployment shared by BFF sessions, security-related temporary state, caches, and rate limits. Local development runs one standalone Redis instance; production may use a standalone, replicated, or clustered deployment without changing application semantics, and the framework does not prescribe Sentinel or sharding initially. Workloads use explicit key namespaces, bounded TTLs, metrics, and capacity controls rather than separate Redis deployments. PostgreSQL remains the recovery source for all authoritative state.
