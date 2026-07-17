# Topic exchange per publishing bounded context

Each CloudForge publishing bounded context owns one durable RabbitMQ topic exchange, such as `iam.events` or `order.events`. Only the owning service may publish to that exchange, while consumers bind their own queues to one or more exchanges. Exchange ownership therefore follows domain ownership, supports least-privilege broker permissions, and avoids an ownerless global event bus.
