# Omnichannel Retail Order Platform

Last Updated: 2026-07-04

---

# Project Goal

Build an enterprise-level Java Spring Boot Microservices project suitable for Senior Java Backend interviews.

The project emphasizes production-quality architecture instead of CRUD.

Primary goals:

- Microservice Architecture
- Event-driven Architecture (Kafka)
- Distributed Transactions (Saga Pattern)
- JWT Authentication
- API Gateway
- Service Discovery (Eureka)
- ShardingSphere
- Omnichannel Order Processing
- Prometheus + Grafana Monitoring
- Docker Deployment
- OpenAPI / Swagger
- Production-quality code

---

# Tech Stack

- Java 17
- Spring Boot 3.5.x
- Spring Cloud
- Spring Security
- Spring Cloud Gateway
- Spring Cloud OpenFeign
- Eureka
- Kafka (KRaft)
- MySQL
- Redis
- ShardingSphere
- Micrometer
- Prometheus
- Grafana
- Docker Compose
- Maven

---

# Services

Infrastructure

- eureka-server
- api-gateway

Business

- user-service
- catalog-service
- cart-service
- promotion-service
- order-service
- inventory-service
- payment-service
- ledger-service
- fulfillment-service

Shared

- common

---

# Current Project Status

Overall Progress

██████████████████████░░ 90%

Completed

✅ Architecture

✅ Authentication

✅ Kafka

✅ Saga

✅ Omnichannel

✅ Promotion

✅ Monitoring

✅ Swagger

✅ Global Exception Handling

✅ Docker Infrastructure

✅ Dockerized Eureka Server

In Progress

🟡 Full Dockerization

Remaining

- Dockerize all Spring Boot services
- Final Docker Compose
- End-to-end verification

---

# Completed Features

## Authentication

Completed

- JWT Authentication
- Spring Security
- Gateway Authentication
- Header Propagation
- HeaderAuthFilter
- Method Security

---

## Order Flow

Implemented

Cart

↓

Promotion Validation

↓

Inventory Reservation

↓

Payment Authorization

↓

Ledger Record

↓

Kafka Event

↓

Fulfillment Creation

---

## Saga Pattern

Completed

Inventory Reserve

↓

Payment Failure

↓

Inventory Compensation

↓

Refund

↓

Ledger Refund Entry

---

## Kafka

Business Events

- Order Created
- Payment Authorized
- Payment Failed
- Inventory Reserved
- Inventory Business Release
- Inventory Expired Release
- Fulfillment Created

---

## Omnichannel Support

Implemented

OrderSource

Supported channels

- WEB
- MOBILE
- STORE
- MARKETPLACE

OrderSource flows through

CreateOrderRequest

↓

Order Entity

↓

Order Event

↓

Promotion Validation

---

## Channel-specific Promotion

Implemented

Promotion

↓

allowedSources

Example

APP20

↓

Allowed

MOBILE

↓

Rejected

STORE

Implemented with

```java
@ElementCollection
private Set<OrderSource> allowedSources;
```

---

## Monitoring

Micrometer business metrics

Implemented metrics

- order_creation_total
- payment_authorized_total
- payment_failed_total
- inventory_reserved_total
- inventory_business_release_total
- inventory_expired_release_total
- fulfillment_creation_total

---

## Prometheus

Completed

Current mode

Spring Boot Services

↓

Host Machine

↓

Prometheus (Docker)

↓

host.docker.internal

---

## Grafana

Completed

Automatic provisioning

Datasource

- Prometheus

Dashboard

- Omnichannel Business Metrics

Panels

- Order Creation Rate
- Payment Authorized Rate
- Payment Failed Rate
- Inventory Reserved Rate
- Inventory Business Release Rate
- Inventory Expired Release Rate
- Fulfillment Creation Rate
- Payment Failure Ratio

---

## OpenAPI / Swagger

Completed

Enabled

- api-gateway
- user-service
- catalog-service
- cart-service
- promotion-service
- order-service
- inventory-service
- payment-service
- ledger-service
- fulfillment-service

Swagger endpoints are publicly accessible.

---

## Global Exception Handling

Completed

Implemented

- GlobalExceptionHandler
- ErrorResponse
- ErrorDetail

HTTP Status mapping

Implemented

- 400
- 401
- 403
- 404
- 500
- 503

Current Response Strategy

Success

ApiResponse<T>

Error

ResponseEntity<ErrorResponse>

Reason

Feign clients still depend on ApiResponse.

---

## Configuration Externalization

Completed

Infrastructure values now support

Local

↓

localhost defaults

Docker

↓

Environment variables

Introduced

- EUREKA_DEFAULT_ZONE
- MYSQL_HOST
- MYSQL_PORT
- KAFKA_BOOTSTRAP_SERVERS
- REDIS_HOST
- REDIS_PORT

Verified

- IntelliJ startup
- ShardingSphere placeholder syntax
- Maven compile

---

# Docker Progress

## Phase 1

Completed

Infrastructure

- MySQL
- Redis
- Kafka (KRaft)
- Prometheus
- Grafana

Added

Redis Healthcheck

MySQL Init Script

Databases

- user_db
- catalog_db
- cart_db
- promotion_db
- inventory_db
- payment_db
- ledger_db
- fulfillment_db
- order_db_0
- order_db_1

---

## Phase 2

Completed

Dockerized

- eureka-server

Verified

- Root Maven Reactor build
- Dockerfile
- Docker Compose
- Healthcheck
- Eureka UI

Current Deployment

Infrastructure

Docker

Business Services

IntelliJ

---

# Remaining Roadmap

## Phase 3 (Current)

Dockerize

- user-service
- catalog-service
- cart-service
- promotion-service

Verify

- Eureka Registration
- MySQL
- Redis
- Feign
- Swagger
- Healthcheck

---

## Phase 4

Dockerize

- inventory-service
- payment-service
- ledger-service
- fulfillment-service

Verify

- Kafka
- Saga
- Metrics
- Prometheus

---

## Phase 5

Dockerize

- order-service
- api-gateway

Verify

- JWT
- Gateway Routing
- Omnichannel Flow
- Promotion
- Inventory
- Payment
- Fulfillment

---

## Phase 6

Production Docker Compose

Complete

- Every Spring Boot service runs in Docker
- Environment variables finalized
- Kafka advertised listeners
- Prometheus scrape targets
- Grafana provisioning
- Healthchecks
- depends_on
- Restart policies

Final Startup

docker compose up -d

---

# Important Architecture Decisions

## Success Response

Current

ApiResponse<T>

Reason

Internal Feign compatibility

Future

May be migrated after Feign refactoring.

---

## Error Response

Current

ResponseEntity<ErrorResponse>

Production-ready

---

## Redis

Actual runtime usage

- cart-service
- catalog-service

Other Redis configurations are currently legacy and can be cleaned up later.

---

## Kafka

Current

localhost:9092

Future Docker

kafka:9092

Advertised listeners will be updated after all services are containerized.

---

## Development Principles

- Prefer incremental changes over large refactors.
- Always keep the project in a compilable state.
- Verify with `mvn clean compile` after each milestone.
- Complete one roadmap phase before starting the next.
- Prioritize interview value over unnecessary complexity.
- Use this document as the single source of truth for future development.

---

# Next Task

Continue Dockerization

Current target

- user-service
- catalog-service
- cart-service
- promotion-service

Do not change business logic.
Focus only on Dockerization.