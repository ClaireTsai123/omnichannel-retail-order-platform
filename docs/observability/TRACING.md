# OpenTelemetry + Zipkin Tracing

Phase 1 enables OpenTelemetry Java Agent tracing only for:

- api-gateway
- order-service

Phase 2 extends Java Agent tracing to the synchronous downstream services called by order-service through Feign:

- cart-service
- inventory-service
- payment-service
- promotion-service

Kafka-based tracing is not enabled yet.

Zipkin runs in Docker Compose and exposes its UI at:

- http://localhost:9411

Prometheus and Grafana remain responsible for metrics. OpenTelemetry and Zipkin are responsible for distributed tracing.

To verify tracing locally:

1. Start the stack with Docker Compose.
2. Call an order API through api-gateway.
3. Open http://localhost:9411 and search for traces from api-gateway and order-service.
