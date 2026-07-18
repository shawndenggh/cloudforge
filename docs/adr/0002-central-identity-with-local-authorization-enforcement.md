# Central identity with local authorization enforcement

CloudForge uses a central identity service to authenticate users, issue credentials, and manage authorization policy, while each business service enforces access locally through shared Spring Security conventions. Business requests must not synchronously call the identity service for every authorization decision, avoiding a platform-wide latency bottleneck and failure dependency.
