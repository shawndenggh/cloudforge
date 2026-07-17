# Quorum queues for production business messages

CloudForge uses durable RabbitMQ quorum queues by default for production business, retry, and dead-letter messages. Production runs at least three RabbitMQ nodes with three queue replicas and combines persistent messages, publisher confirms, and explicit consumer acknowledgements; local single-node environments declare the same queue type for behavioral parity without claiming high availability. Classic queues require an explicit, non-critical, loss-tolerant use case.
