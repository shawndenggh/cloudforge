# Direct Spring AMQP for RabbitMQ

CloudForge uses Spring AMQP directly for RabbitMQ integration and does not introduce Spring Cloud Stream initially. The platform owns explicit exchange, queue, routing-key, publisher-confirm, acknowledgement, retry, dead-letter, Outbox/Inbox, and idempotency conventions; broker portability is not a current requirement, so an additional binder abstraction would add configuration and operational indirection without a concrete benefit.
