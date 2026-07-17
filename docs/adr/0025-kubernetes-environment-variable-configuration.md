# Kubernetes environment variable configuration

CloudForge does not use Spring Cloud Config initially. Each service receives ordinary key-value configuration from its own Kubernetes ConfigMap and sensitive values from narrowly scoped Secret references as environment variables, which Spring Boot binds to validated `@ConfigurationProperties`. Certificates, private keys, trust stores, and other file-shaped secrets are mounted read-only instead. Configuration changes are applied through a Pod rollout rather than runtime refresh, so applications do not need Kubernetes API read permissions.
