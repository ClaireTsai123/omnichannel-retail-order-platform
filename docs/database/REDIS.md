# Redis Usage

## Overview

Redis is used in this project for fast, temporary, high-read application data. The current implementation uses Redis in two places:

- `cart-service` stores shopping cart state in Redis.
- `catalog-service` uses Spring Cache backed by Redis for product lookups.

Redis is provided locally by Docker Compose on port `6379`.

## Cart Storage

`cart-service` uses `RedisTemplate<String, Object>` to store cart data as Redis hashes.

Key pattern:

```text
cart:<userId>
```

Hash field:

```text
<productId>
```

Hash value:

```text
CartItemDTO
```

The cart service supports:

- get cart
- add item
- update item quantity
- remove item
- clear cart

Cart totals are calculated from Redis hash values when the cart is read.

## Cart Expiration

Cart keys are given a TTL when they are read or modified.

Current code constant:

```text
CART_TTL_MINUTES = 300
```

The inline comment says this value is for testing, so it should be treated as a local/demo setting rather than a final production TTL.

## Catalog Caching

`catalog-service` uses Spring Cache annotations for product queries:

- `@Cacheable` for all active products
- `@Cacheable` for product lookup by ID
- `@CacheEvict` when products are created, updated, or deleted

Current cache name in code:

```text
menu:all
```

The domain is now product catalog rather than menu, so the cache name is a legacy naming detail. It is documented here as-is to match the current implementation.

## Configuration

Redis host and port are environment-driven:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
```

Docker Compose sets service-to-service Redis access with:

```text
REDIS_HOST=redis
REDIS_PORT=6379
```

`cart-service` configures Redis serialization with:

- `StringRedisSerializer` for keys and hash keys
- `JdkSerializationRedisSerializer` for values and hash values

## Why Redis Fits This Project

Redis is appropriate here because:

- carts are temporary and user-specific
- cart reads/writes should be low latency
- inactive carts can expire automatically
- catalog reads can be cached to reduce database load
- Redis keeps the local Docker architecture close to common production patterns

## Current Limitations

Current limitations:

- cart values use Java serialization, which is convenient locally but less portable than JSON for cross-language systems
- no Redis cluster is configured
- no explicit cache versioning strategy is implemented
- catalog cache naming still uses the legacy `menu:all` label
- Redis is not used as a message broker in this project

## Production Considerations

A production version would typically add:

- managed Redis or Redis Cluster
- TLS and authentication
- more intentional TTL values per data type
- JSON or another portable serialization format
- cache key versioning
- cache hit/miss dashboards
- alerting for memory pressure and eviction rates

## Interview Talking Points

- Cart data is kept out of MySQL because it is temporary, frequently updated, and does not require long-term relational persistence.
- Product caching reduces repeated catalog database reads.
- The system uses Redis for low-latency state, while MySQL remains the durable system of record.
- TTL-based expiration helps prevent abandoned carts from accumulating indefinitely.
