# HTTP Service Clients for synchronous calls

CloudForge uses Spring HTTP Service Clients backed by `RestClient` for ordinary synchronous service-to-service calls, and does not introduce OpenFeign or `RestTemplate`. Consumers own their client interfaces and transport DTOs, while shared infrastructure propagates security, Current Tenant, and trace context and applies explicit timeout and idempotency-aware retry policies.
