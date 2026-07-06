# OpenTelemetry + Zipkin Tracing

## 1. Overview

OpenTelemetry and Zipkin were added to provide distributed tracing across the core order-processing path of the platform.

The platform is split across multiple Spring Boot microservices, and a single business request can cross the API Gateway, synchronous HTTP/Feign calls, and asynchronous Kafka events. Distributed tracing makes that end-to-end flow visible in one place, which helps with debugging latency, identifying failing service boundaries, and explaining how a request moves through the system.

Zipkin is used as the local tracing backend and UI.

## 2. Scope

Tracing is currently enabled for the core Saga/order execution path:

- api-gateway
- order-service
- cart-service
- inventory-service
- promotion-service
- payment-service
- ledger-service
- fulfillment-service

This scope covers:

- Gateway entry requests.
- Order orchestration.
- Synchronous downstream HTTP/Feign calls.
- Kafka producer and consumer flows.
- Payment, ledger, and fulfillment event processing.

## 3. Non-Instrumented Services

The following services are intentionally not instrumented in this tracing phase:

- catalog-service
- user-service
- eureka-server

catalog-service and user-service are not part of the current core Saga/order execution path. They can be instrumented later if product browsing, customer profile, authentication, or authorization flows require end-to-end tracing.

eureka-server is infrastructure for service discovery. It is useful operationally, but it is not part of the business request flow being traced in this phase.

## 4. Architecture

The current tracing path focuses on the order lifecycle:

```text
Client
  -> API Gateway
  -> Order Service
  -> downstream Feign services
       -> Cart Service
       -> Inventory Service
       -> Promotion Service
       -> Payment Service
  -> Kafka
       -> Ledger Service
       -> Fulfillment Service
       -> Order Service
```

OpenTelemetry propagates trace context across HTTP boundaries and Kafka message headers so related spans can be correlated in Zipkin.

## 5. Configuration

Tracing is enabled with the OpenTelemetry Java Agent. No manual spans or custom tracing code are required for the current setup.

The instrumented services use the Zipkin exporter:

```text
OTEL_TRACES_EXPORTER=zipkin
OTEL_EXPORTER_ZIPKIN_ENDPOINT=http://zipkin:9411/api/v2/spans
```

Each service defines its own service name so traces are grouped clearly in Zipkin:

```text
OTEL_SERVICE_NAME=<service-name>
```

Kafka and HTTP trace propagation use:

```text
OTEL_PROPAGATORS=tracecontext,baggage,b3
```

Metrics and logs are disabled in the OpenTelemetry agent because this phase uses the agent only for tracing:

```text
OTEL_METRICS_EXPORTER=none
OTEL_LOGS_EXPORTER=none
```

Prometheus and Grafana continue to handle metrics separately.

## 6. Verification

To verify tracing locally:

1. Start the stack with Docker Compose:

   ```bash
   docker compose up --build
   ```
   For incremental verification after tracing-only changes, restart only the affected services with docker compose up -d --build <service-names>.

2. Trigger a create order flow through api-gateway.

3. Trigger a payment flow so payment-service publishes Kafka events and ledger-service consumes them.

4. Trigger a fulfillment status update flow so fulfillment-service publishes Kafka events and order-service consumes them.

5. Open Zipkin:

   ```text
   http://localhost:9411
   ```

6. Search by service name, such as:

   ```text
   api-gateway
   order-service
   payment-service
   ledger-service
   fulfillment-service
   ```

Zipkin should show related spans grouped into distributed traces across HTTP and Kafka boundaries.

## 7. Expected Traces

Example synchronous order validation trace:

```text
api-gateway
  -> order-service
  -> cart-service
  -> inventory-service
  -> promotion-service
```

Example payment and ledger trace:

```text
api-gateway
  -> order-service
  -> payment-service
  -> Kafka payment-events
  -> ledger-service
  -> order-service
```

Example fulfillment status trace:

```text
api-gateway
  -> fulfillment-service
  -> Kafka fulfillment-events
  -> order-service
```

Kafka traces should include producer and consumer spans when a message is published and then consumed by another service.

## 8. Metrics vs Tracing

Prometheus and Grafana are used for metrics.

Examples:

- Request counts.
- JVM metrics.
- Service health.
- Latency metrics.
- Dashboard visualization.

OpenTelemetry and Zipkin are used for distributed tracing.

Examples:

- Following one request across multiple services.
- Connecting HTTP calls with Kafka publish and consume spans.
- Finding which service boundary added latency.
- Debugging broken or incomplete business flows.

## 9. Interview Talking Points

In a Senior Java Backend interview, this tracing setup can be described as follows:

- The platform uses OpenTelemetry Java Agent auto-instrumentation, avoiding manual tracing code and keeping business logic clean.
- Zipkin is used locally as the tracing backend to visualize request flow across microservices.
- HTTP and Feign tracing show synchronous service-to-service calls from the gateway through the order orchestration path.
- Kafka propagation is enabled so asynchronous producer and consumer spans can be correlated with the original business flow.
- Metrics and tracing are intentionally separated: Prometheus/Grafana are used for operational metrics, while OpenTelemetry/Zipkin are used for distributed request tracing.
- The rollout was phased to reduce risk: first gateway and order-service, then downstream HTTP services, then Kafka producers and consumers.
