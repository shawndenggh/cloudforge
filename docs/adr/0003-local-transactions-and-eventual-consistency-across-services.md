# Local transactions and eventual consistency across services

CloudForge keeps ACID transactions inside one service-owned database and uses Transactional Outbox/Inbox, RabbitMQ at-least-once delivery, idempotent consumers, Saga compensation, and reconciliation for workflows that cross service boundaries. Seata and XA/2PC are not part of the initial platform; business invariants that require immediate atomic consistency must remain inside one service boundary.
