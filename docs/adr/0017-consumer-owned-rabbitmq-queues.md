# Consumer-owned RabbitMQ queues

CloudForge producers own exchanges and event publication, while each logical consumer owns its durable queue and bindings. Different consumers of the same event use different queues; replicas of one consumer compete on the same queue. Independent handlers use separate queues when their retry, throughput, failure, or lifecycle requirements differ, keeping subscriptions and failure handling decoupled from producers.
