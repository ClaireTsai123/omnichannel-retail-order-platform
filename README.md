# Omnichannel Retail Order Platform

## Project Overview

Omnichannel Retail Order Platform is a multi-module Spring Boot microservices project that models an end-to-end retail order workflow: user authentication with JWT access tokens and DB-backed refresh tokens, product browsing, cart management, promotion validation, order creation, inventory reservation, payment authorization, ledger auditing, and fulfillment status updates.

The current implementation is runnable locally with Docker Compose and includes service discovery, an API gateway, MySQL, Redis, Kafka, Prometheus, Grafana, OpenTelemetry, and Zipkin.

## Architecture Summary

```text
Client
  -> API Gateway
  -> Eureka-discovered Spring Boot services
  -> MySQL / Redis / Kafka
  -> Prometheus + Grafana metrics
  -> OpenTelemetry + Zipkin tracing for the core order path
```

The platform combines synchronous HTTP/Feign calls for request orchestration with Kafka events for asynchronous state propagation. Order data is sharded across two MySQL databases through Apache ShardingSphere.

## Services

| Module                | Port | Responsibility                                                                                    |
|-----------------------|-----:|---------------------------------------------------------------------------------------------------|
| `api-gateway`         | 8080 | Spring Cloud Gateway routing, JWT access-token validation, trusted user header propagation        |
| `eureka-server`       | 8761 | Local service discovery registry                                                                  |
| `user-service`        | 8081 | User registration, login, refresh token rotation, logout, user lookup                             |
| `catalog-service`     | 8082 | Product catalog APIs and Redis-backed caching                                                     |
| `cart-service`        | 8083 | Shopping cart APIs backed by Redis                                                                |
| `order-service`       | 8084 | Order orchestration, ShardingSphere order persistence, inventory/payment/fulfillment coordination |
| `fulfillment-service` | 8085 | Fulfillment record management and fulfillment status events                                       |
| `inventory-service`   | 8086 | Inventory lookup, reservation, commit, release, and reservation expiration handling               |
| `payment-service`     | 8087 | Payment authorization, failure simulation, refund/failure events, idempotency key handling        |
| `ledger-service`      | 8088 | Ledger entries from payment events                                                                |
| `promotion-service`   | 8089 | Promotion validation by code and order source                                                     |
| `common`              |  n/a | Shared DTOs, events, enums, exceptions, security helpers, and Kafka topic constants               |

## Core Business Flow

```text
Register/Login
  -> Browse catalog
  -> Add products to cart
  -> Create order
  -> Reserve inventory
  -> Apply promotion when provided
  -> Authorize payment
  -> Commit or release inventory
  -> Publish payment/order events
  -> Create ledger entries
  -> Create or update fulfillment
  -> Update order status
```

The order flow uses a pragmatic Saga-style design. `order-service` coordinates synchronous calls to cart, inventory, promotion, and payment services, while Kafka events keep downstream ledger and fulfillment workflows decoupled.

## Kafka Event Flow

Only the event flows below are actively implemented with `KafkaTemplate` producers and `@KafkaListener` consumers in the codebase.

| Producer              | Topic                | Consumer              | Purpose                                                                                                                |
|-----------------------|----------------------|-----------------------|------------------------------------------------------------------------------------------------------------------------|
| `order-service`       | `order-events`       | `fulfillment-service` | Fulfillment reacts to `ORDER_PAID` by creating fulfillment records and to `ORDER_CANCELLED` by cancelling fulfillment. |
| `payment-service`     | `payment-events`     | `ledger-service`      | Ledger creates authorization or refund ledger entries from payment events.                                             |
| `payment-service`     | `payment-events`     | `order-service`       | Order status is updated for payment success/failure; failed payments trigger inventory release.                        |
| `fulfillment-service` | `fulfillment-events` | `order-service`       | Order status is updated from fulfillment status changes such as processing, shipped, delivered, or cancelled.          |

```text
Order Service -> order-events -> Fulfillment Service
Payment Service -> payment-events -> Ledger Service
Payment Service -> payment-events -> Order Service
Fulfillment Service -> fulfillment-events -> Order Service
```

The shared `common` module also defines additional topic constants and DLQ topic names. Some of those are reserved or partially configured for future expansion and are not listed above unless an active producer/consumer path exists.

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

## Data and Persistence

- MySQL databases are initialized through `docker/mysql/init`.
- Service-owned databases include user, catalog, inventory, payment, ledger, fulfillment, promotion, and two order shard databases.
- `order-service` uses ShardingSphere with `order_db_0` and `order_db_1`.
- Redis is used for cart storage and catalog caching.
- Inventory uses a reservation model with reserve, commit, release, and expiration paths.

## Observability

### Metrics

Spring Boot Actuator exposes Prometheus metrics from the services through `/actuator/prometheus`. The repository includes:

- `prometheus.yml` scrape configuration
- Grafana datasource provisioning
- Grafana dashboard provisioning
- A business metrics dashboard JSON

Custom Micrometer counters are implemented for order creation, payment authorization/failure, inventory reservation/release, and fulfillment creation.

### Tracing

OpenTelemetry Java Agent and Zipkin are configured for the core order-processing path. The Dockerfiles for the main order workflow services install the OpenTelemetry Java agent, and Docker Compose provides Zipkin at:

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
- Inventory reservation pattern to reduce overselling risk.
- Payment idempotency key handling.
- Ledger audit trail from payment events.
- Sharded order storage with ShardingSphere.
- Redis-backed cart and catalog caching.
- Prometheus/Grafana metrics and OpenTelemetry/Zipkin tracing for operational visibility.
- Environment-variable based secret handling for local Compose configuration.

## Production-Readiness Notes

This repository currently implements a local Docker Compose environment. Production deployment to AWS ECS, Kubernetes, Helm, or other platforms is not implemented in the tracked application code.

A production version would typically add:

- Managed secret storage and rotation
- Image registry publishing, such as AWS ECR
- ECS or Kubernetes deployment manifests
- Environment-specific configuration
- Database migration tooling and validation
- Centralized logging
- Stronger authentication and authorization hardening
- CI test reporting, coverage, vulnerability scanning, and image scanning
- Smoke tests after deployment
- Manual approval gates for production
- Rollback, Blue-Green, or Canary deployment strategies

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
- Prometheus metrics and Grafana provisioning
- OpenTelemetry + Zipkin tracing for the core order path
- ShardingSphere configuration for order data

Not currently claimed as implemented:

- Real AWS ECS deployment
- Real Kubernetes deployment
- Docker image push to a remote registry
- Production secrets management
- Blue-Green or Canary deployment automation
