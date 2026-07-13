# Omnichannel Retail Order Platform

## Project Overview

Omnichannel Retail Order Platform is a Java 17 / Spring Boot multi-module microservices project that models a realistic retail order-management workflow: authentication, catalog browsing, cart management, order creation, inventory reservation, synchronous payment authorization, asynchronous payment/order/fulfillment events, ledger recording, and item-level fulfillment tracking.

The current implementation is designed for local development with Docker Compose. It includes Eureka service discovery, API Gateway routing, MySQL, Redis, Kafka, Prometheus, Grafana, OpenTelemetry, and Zipkin. The core OMS flow is implemented in application code; production deployment automation, production migrations, transactional outbox/inbox, node-aware inventory allocation, and advanced warehouse optimization are intentionally not claimed as complete.

## Architecture Summary

```text
Client
  -> API Gateway
  -> Eureka-discovered Spring Boot services
  -> MySQL / Redis / Kafka
  -> Prometheus + Grafana metrics
  -> OpenTelemetry + Zipkin tracing for the core order path
```

The platform uses synchronous HTTP/Feign calls for command-style orchestration and Kafka for asynchronous state propagation. `order-service` owns the order lifecycle and coordinates cart, inventory, promotion, and payment calls. `fulfillment-service` owns fulfillment records and fulfillment lines. `payment-service` owns payment authorization state and publishes payment events. `ledger-service` consumes payment events for ledger entries.

Order data is sharded across two MySQL order databases through Apache ShardingSphere.

## Services and Boundaries

| Module                | Port | Responsibility                                                                                                                                           |
|-----------------------|-----:|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `api-gateway`         | 8080 | Spring Cloud Gateway routing, JWT access-token validation, trusted user header propagation                                                               |
| `eureka-server`       | 8761 | Local service discovery registry                                                                                                                         |
| `user-service`        | 8081 | User registration, login, refresh token rotation, logout, user lookup                                                                                    |
| `catalog-service`     | 8082 | Product catalog APIs and Redis-backed caching                                                                                                            |
| `cart-service`        | 8083 | Shopping cart APIs backed by Redis                                                                                                                       |
| `order-service`       | 8084 | Order orchestration, order status transitions, order status history, payment failure handling, fulfillment status aggregation, sharded order persistence |
| `fulfillment-service` | 8085 | Multiple fulfillments per order, fulfillment lines, item-level fulfillment status, node/location metadata, fulfillment events                            |
| `inventory-service`   | 8086 | Inventory lookup, reservation, commit, release, and reservation expiration                                                                               |
| `payment-service`     | 8087 | Payment authorization, payment failure simulation, payment events, idempotency key handling                                                              |
| `ledger-service`      | 8088 | Ledger entries from payment events                                                                                                                       |
| `promotion-service`   | 8089 | Promotion validation by code and order source                                                                                                            |
| `common`              |  n/a | Shared DTOs, events, enums, exceptions, security helpers, and Kafka topic constants                                                                      |

## Core End-to-End Order Flow

```text
1. Client adds items to cart.
2. Client creates an order.
3. order-service reads cart data and reserves inventory synchronously through inventory-service.
4. order-service applies promotion when provided.
5. order-service persists the order and order items with status CREATED.
6. order-service stores the optional checkout `idempotencyKey` with the order.
7. order-service records order_status_history for ORDER_CREATED in the same local transaction as the order insert.
8. order-service reserves inventory outside the local order database transaction.
9. order-service publishes OrderEvent(ORDER_CREATED) only after the local order insert has committed and inventory reservation has succeeded.
10. Client calls pay order.
11. order-service calls payment-service synchronously to authorize payment outside the local order database transaction.
12. payment-service persists payment state and publishes PaymentEvent.
13. If authorization succeeds:
    - order-service transitions CREATED -> PAID.
    - order-service records status history.
    - order-service commits inventory outside the local order database transaction.
    - order-service publishes OrderEvent(ORDER_PAID) with order items after the PAID update commits.
14. fulfillment-service consumes ORDER_PAID and creates the default fulfillment and fulfillment lines.
15. fulfillment-service publishes fulfillment status events when fulfillment or line status changes.
16. order-service consumes fulfillment events, stores per-fulfillment status snapshots, and derives the order-level fulfillment status.
17. ledger-service consumes payment events and creates ledger records.
```

This is a pragmatic Saga-style flow. The primary payment command flow is synchronous in `payOrder()`, while Kafka events keep ledger, fulfillment, and reconciliation paths decoupled.

## Checkout Request Idempotency

`CreateOrderRequest` includes an optional `idempotencyKey`.

Current behavior:

- `order-service` checks `(userId, idempotencyKey)` before loading the cart or reserving inventory.
- if the same user submits the same key again, the existing order is returned.
- duplicate idempotent requests do not reserve inventory again.
- duplicate idempotent requests do not publish another `ORDER_CREATED` event.
- duplicate idempotent requests do not clear the cart again.
- `orders` has a database unique constraint on `(user_id, idempotency_key)`.
- concurrent identical requests are protected by the unique constraint; if a request loses the insert race, `order-service` catches the unique-constraint failure, reloads the existing order, and returns it.

Requests without an `idempotencyKey` still follow the legacy behavior and can create a new order each time. Production clients should send a stable key for each checkout attempt.

## Order Concurrency and Transaction Boundaries

`orders` uses optimistic locking through a JPA `@Version` column named `version`.

Current behavior:

- concurrent stale updates to the same order are detected by the database version check.
- stale update conflicts are translated to HTTP `409 Conflict`.
- status-transition validation is still preserved; invalid late transitions are ignored or rejected according to the existing rules.
- order status and `order_status_history` are written in the same short local transaction.
- Feign calls to cart, inventory, promotion, and payment services are no longer made inside the short local persistence transactions.
- order Kafka events are published only after the related local database transaction returns successfully.

This improves local consistency and concurrency handling, but it is still not a distributed transaction. If a remote call succeeds after the local transaction commits and a later remote call fails, reconciliation or compensation may still be required.

## Payment Failure Handling

Payment failure is centralized in `order-service` through `handlePaymentFailed(orderId, eventId, source)`.

Both paths reuse the same handler:

- synchronous payment authorization failure inside `payOrder()`
- asynchronous `PaymentEvent(PAYMENT_FAILED)` consumed by `PaymentEventConsumer`

Current behavior:

- `PAYMENT_FAILED` transitions an eligible `CREATED` order to `PAYMENT_FAILED`.
- reserved inventory is released only when the order is still `CREATED`.
- already `PAYMENT_FAILED` or `CANCELLED` orders are handled idempotently.
- late payment failures for orders that are already `PAID`, `PROCESSING`, `SHIPPED`, or `DELIVERED` are ignored and do not release committed inventory.
- synchronous payment failure no longer ends the order as `CANCELLED`; it uses `PAYMENT_FAILED`.

`PAYMENT_AUTHORIZED` events are consumed by `order-service` only as an acknowledgement/reconciliation signal. The real `CREATED -> PAID` transition is owned by the synchronous `payOrder()` command after successful authorization.

## Kafka Topics and Event Flow

Active topics used by implemented producers/consumers:

| Topic                | Producer              | Consumer              | Purpose                                                                                                                                  |
|----------------------|-----------------------|-----------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| `order-events`       | `order-service`       | `fulfillment-service` | `ORDER_PAID` creates default fulfillment lines; `ORDER_CANCELLED` cancels existing fulfillments                                          |
| `payment-events`     | `payment-service`     | `order-service`       | `PAYMENT_FAILED` uses centralized payment failure handling; `PAYMENT_AUTHORIZED` is acknowledged without duplicating the PAID transition |
| `payment-events`     | `payment-service`     | `ledger-service`      | Creates ledger entries for payment activity                                                                                              |
| `fulfillment-events` | `fulfillment-service` | `order-service`       | Updates per-fulfillment status snapshots and derives order-level fulfillment status                                                      |

Topic constants also exist for:

- `payment-events-dlq`
- `fulfillment-events-dlq`
- `inventory-events`
- `ledger-events`
- `ledger-events-dlq`
- `ledger-payment-events-dlq`
- `fulfillment-order-events-dlq`

Some constants are reserved or used only by error-handler configuration. They should not be read as fully implemented business flows unless there is an active producer and consumer path in code.

### Event Metadata

The active shared event contracts include basic metadata:

- `eventId`
- `occurredAt`
- `version`

Implemented event payloads:

- `OrderEvent`: event metadata, event type, order id, user id, total amount, source, and order items.
- `PaymentEvent`: event metadata, event type, payment id, order id, user id, and amount.
- `FulfillmentStatusUpdatedEvent`: event metadata, fulfillment id, fulfillment number, order id, fulfillment node/location fields, fulfillment status, and fulfillment line details.
- `FulfillmentLineEvent`: line id, order item id, product id, SKU, fulfilled quantity, ordered quantity, and line status.

## Consumer Idempotency and Late Events

`order-service` stores processed Kafka events in `processed_kafka_events` using a unique `eventId`.

Current behavior:

- duplicate payment events are skipped by `PaymentEventConsumer`.
- duplicate fulfillment events are skipped by `FulfillmentEventConsumer`.
- missing event ids fall back to deterministic synthetic ids based on topic and business identifiers.
- late or regressive fulfillment events do not move a fulfillment snapshot backwards.
- late payment failure events do not modify orders that have already moved beyond `CREATED`.

This is practical local idempotency, not a full production transactional inbox/outbox implementation.

## Order Status Audit History

`order-service` records order status transitions in `order_status_history`.

Recorded fields include:

- `orderId`
- previous status
- new status
- reason
- source
- event id when available
- created timestamp

Status history is recorded for creation, payment success, payment failure, cancellation, and fulfillment-driven status aggregation.

## Fulfillment Model

The project now supports multiple fulfillment records for one order.

Key points:

- `fulfillments.order_id` is not treated as unique.
- each fulfillment has its own `id` and `fulfillmentNo`.
- status updates use `fulfillmentId`, not only `orderId`.
- fulfillment lookup by order returns a list.
- duplicate creation with the same `orderId + fulfillmentNo` returns the existing fulfillment and does not allocate quantities again.

### Fulfillment Lines and Item-Level Status

Fulfillment records contain fulfillment lines. Each line references an order item and stores:

- `orderItemId`
- `productId`
- `sku`
- `quantity`
- `orderedQuantity`
- line-level fulfillment status

Line status updates can derive the parent fulfillment status:

- all cancelled lines -> fulfillment `CANCELLED`
- all delivered or cancelled lines -> fulfillment `DELIVERED`
- any shipped or delivered line -> fulfillment `SHIPPED`
- any processing line -> fulfillment `PROCESSING`
- otherwise -> fulfillment `CREATED`

Fulfillment-level status updates still exist and update all lines, but item-level line updates are the safer path for split fulfillment scenarios.

### Quantity Validation Across Fulfillments

Fulfillment creation validates allocation at the `orderId + orderItemId` level.

Rules:

- `orderItemId` is required for each fulfillment line.
- `quantity` must be greater than zero.
- `orderedQuantity`, when provided, must be greater than zero.
- duplicate `orderItemId` values inside one fulfillment request are rejected.
- a single fulfillment line cannot exceed the ordered quantity.
- total non-cancelled fulfillment quantity across all fulfillments for the same order item cannot exceed `orderedQuantity`.
- later requests must use the same established `orderedQuantity` for the same order item.
- cancelled fulfillment lines do not count toward currently allocated quantity.

This prevents a split fulfillment from allocating more units than the customer ordered.

### Fulfillment Node and Location Metadata

Fulfillment records include a practical node/location model:

- `nodeId`
- `nodeName`
- `nodeType`
- `locationCode`

These fields are included in fulfillment API responses and fulfillment Kafka events. If no node is supplied, the service uses local defaults:

- `DEFAULT_NODE`
- `Default Fulfillment Node`
- `WAREHOUSE`
- `DEFAULT_LOCATION`

This metadata describes where a fulfillment is handled, but it is not automatic node allocation and inventory is not reserved per node yet.

## Order-Level Fulfillment Aggregation

`order-service` consumes `fulfillment-events` and stores one fulfillment status snapshot per `fulfillmentId` in `order_fulfillment_status`.

The order-level status is derived from all known fulfillment records for that order:

- all fulfillment snapshots cancelled -> order `CANCELLED`
- all fulfillment snapshots delivered or cancelled -> order `DELIVERED`
- any fulfillment shipped or delivered -> order `SHIPPED`
- otherwise -> order `PROCESSING`

This avoids directly mapping one fulfillment event to the whole order status. It supports split fulfillment where one order can have multiple fulfillment records moving independently.

## Current Fulfillment API Shape

Main fulfillment endpoints:

```text
GET   /api/fulfillment/orders/{orderId}
GET   /api/fulfillment/{fulfillmentId}
POST  /api/fulfillment/orders/{orderId}
PATCH /api/fulfillment/{fulfillmentId}/status
PATCH /api/fulfillment/{fulfillmentId}/lines/{lineId}/status
```

`POST /api/fulfillment/orders/{orderId}` accepts:

- `userId`
- `fulfillmentNo`
- `nodeId`
- `nodeName`
- `nodeType`
- `locationCode`
- `lines[]` with `orderItemId`, `productId`, `sku`, `quantity`, and `orderedQuantity`

The response includes fulfillment metadata, node/location fields, fulfillment status, timestamps, and fulfillment lines.

## Data and Persistence

- MySQL databases are initialized through `docker/mysql/init`.
- Service-owned databases include user, catalog, inventory, payment, ledger, fulfillment, promotion, and two order shard databases.
- `order-service` uses ShardingSphere with `order_db_0` and `order_db_1`.
- Redis is used for cart storage and catalog caching.
- Inventory uses a reservation model with reserve, commit, release, and expiration paths.
- `order-service` stores status history and processed Kafka event ids.
- `fulfillment-service` stores multiple fulfillments per order and item-level fulfillment lines.

## Tech Stack

- Java 17
- Spring Boot 3.5.x
- Spring Cloud 2025.x
- Spring Cloud Gateway
- Netflix Eureka
- Spring Security, JWT, and DB-backed refresh tokens
- Spring Data JPA / Hibernate
- OpenFeign
- Apache Kafka
- MySQL 8
- Redis 7
- Apache ShardingSphere JDBC
- Micrometer and Spring Boot Actuator
- Prometheus
- Grafana
- OpenTelemetry Java Agent
- Zipkin
- Docker and Docker Compose
- Maven multi-module build

## Observability

### Metrics

Spring Boot Actuator exposes Prometheus metrics from the services through `/actuator/prometheus`. The repository includes:

- `prometheus.yml` scrape configuration
- Grafana datasource provisioning
- Grafana dashboard provisioning
- a business metrics dashboard JSON

Custom Micrometer counters are implemented for order creation, payment authorization/failure, inventory reservation/release, and fulfillment creation.

### Tracing

OpenTelemetry Java Agent and Zipkin are configured for the core order-processing path. Docker Compose provides Zipkin at:

```text
http://localhost:9411
```

Tracing currently focuses on:

- `api-gateway`
- `cart-service`
- `order-service`
- `inventory-service`
- `promotion-service`
- `payment-service`
- `ledger-service`
- `fulfillment-service`

`user-service`, `catalog-service`, and `eureka-server` are not part of the current tracing scope.

## Docker Compose Quick Start

1. Create a local environment file from the example:

   ```bash
   cp .env.example .env
   ```

2. Set local-only values in `.env`, especially:

   ```text
   DB_USERNAME=<local-db-user>
   DB_PASSWORD=<local-db-password>
   GRAFANA_PASSWORD=<local-grafana-password>
   ```

3. Build and start the full local stack:

   ```bash
   docker compose up --build
   ```

4. Useful local URLs:

   | Component         | URL                     |
   |-------------------|-------------------------|
   | API Gateway       | `http://localhost:8080` |
   | Eureka            | `http://localhost:8761` |
   | Prometheus        | `http://localhost:9090` |
   | Grafana           | `http://localhost:3000` |
   | Zipkin            | `http://localhost:9411` |
   | Kafka bootstrap   | `localhost:9092`        |
   | MySQL mapped port | `localhost:3307`        |

5. Stop the stack:

   ```bash
   docker compose down
   ```

Use `docker compose down -v` only when you intentionally want to remove local persisted MySQL, Redis, Kafka, Prometheus, and Grafana volumes.

## Key Engineering Highlights

- Multi-module Maven project with a shared `common` module.
- Clear service boundaries for users, catalog, cart, orders, inventory, payment, ledger, fulfillment, and promotions.
- API Gateway routes external requests to service-discovered backend services.
- JWT access-token validation at the gateway with downstream user context propagation.
- DB-backed refresh token rotation and logout in `user-service`.
- Local Docker Compose stack for repeatable end-to-end development and verification.
- Kafka-backed asynchronous flows for payment, ledger, fulfillment, and order status updates.
- Synchronous Feign orchestration for the core order creation/payment path.
- Checkout request idempotency through `CreateOrderRequest.idempotencyKey`.
- Optimistic locking on orders through the `version` column.
- Short order-service database transactions around local writes, with Feign calls outside those transaction blocks.
- Centralized payment failure handling in `order-service`.
- Inventory reservation pattern to reduce overselling risk.
- Payment idempotency key handling.
- Ledger audit trail from payment events.
- Order status audit history.
- Consumer idempotency through processed Kafka event ids.
- Multiple fulfillment records per order.
- Fulfillment lines with item-level fulfillment status.
- Cross-fulfillment quantity validation per order item.
- Fulfillment node/location metadata.
- Order-level fulfillment status aggregation across multiple fulfillments.
- Sharded order storage with ShardingSphere.
- Redis-backed cart and catalog caching.
- Prometheus/Grafana metrics and OpenTelemetry/Zipkin tracing for operational visibility.
- Environment-variable based secret handling for local Compose configuration.

## Known Limitations and Future Phases

Implemented for local realism, but not production-complete:

- no automatic fulfillment node allocation planner
- no node-aware inventory reservation
- no advanced warehouse management, route planning, carrier integration, or package/tracking model
- no partial refunds or full payment lifecycle redesign
- no production database migration scripts for existing live data
- no transactional outbox/inbox implementation
- no full distributed transaction across order, inventory, payment, and Kafka
- no dead-letter replay tooling beyond DLQ topic configuration
- no distributed locking or database-level allocation lock for high-concurrency fulfillment creation
- no real external payment provider integration
- no production deployment manifests for AWS ECS, Kubernetes, or Helm
- no production secrets manager integration
- no Blue-Green or Canary deployment automation

Future phases would normally add:

- production migration scripts and backfills for fulfillment lines, ordered quantities, and node metadata
- inventory availability/reservation by fulfillment node
- allocation planning across warehouses, stores, vendors, temperature zones, or delivery networks
- shipment/package/tracking entities
- stronger concurrency control for fulfillment allocation
- transactional outbox/inbox and replay tooling
- reconciliation jobs for locally committed order states where a later remote side effect failed
- richer return, refund, cancellation, and exception workflows

## Production-Readiness Notes

This repository currently implements a local Docker Compose environment. Production deployment to AWS ECS, Kubernetes, Helm, or other platforms is not implemented in the tracked application code.

A production version would typically add:

- managed secret storage and rotation
- image registry publishing, such as AWS ECR
- ECS or Kubernetes deployment manifests
- environment-specific configuration
- database migration tooling and validation
- centralized logging
- stronger authentication and authorization hardening
- CI test reporting, coverage, vulnerability scanning, and image scanning
- smoke tests after deployment
- manual approval gates for production
- rollback, Blue-Green, or Canary deployment strategies

These are production architecture considerations, not claimed as completed local implementation.

## Documentation

Additional tracked documentation is available in:

- `docs/api/API_DOCUMENTATION.md`
- `docs/database/MYSQL_SHARDING.md`
- `docs/database/REDIS.md`
- `docs/observability/TRACING.md`

## Current Status

Implemented locally:

- Spring Boot multi-module microservices
- Docker Compose full-stack runtime
- MySQL, Redis, Kafka, Eureka, Prometheus, Grafana, and Zipkin
- API Gateway routing
- Core order, payment, inventory, ledger, fulfillment, promotion, catalog, cart, and user workflows
- JWT login, protected gateway access, refresh token rotation, and logout
- Kafka producers and consumers for the active event flows listed above
- event metadata on active shared event contracts
- checkout request idempotency for create-order
- optimistic locking for order updates
- shorter order-service transaction boundaries around local writes
- order status history
- processed Kafka event tracking for order-service payment and fulfillment consumers
- centralized payment failure handling
- multiple fulfillments per order
- fulfillment lines and item-level fulfillment status
- fulfillment quantity validation across multiple fulfillments
- fulfillment node/location metadata
- fulfillment status aggregation into order status
- Prometheus metrics and Grafana provisioning
- OpenTelemetry + Zipkin tracing for the core order path
- ShardingSphere configuration for order data

Not currently claimed as implemented:

- real AWS ECS deployment
- real Kubernetes deployment
- Docker image push to a remote registry
- production secrets management
- production-grade database migration scripts
- transactional outbox/inbox
- automatic node allocation
- node-aware inventory reservation
- Blue-Green or Canary deployment automation
