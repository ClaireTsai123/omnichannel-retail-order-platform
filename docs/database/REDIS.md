# Redis Design

## Purpose

Redis is used to provide fast, in-memory data access for frequently changing or high-traffic data.

---

## Usage in the System

### 1. Shopping Cart

- Cart data is stored in Redis per user
- **Key format:** `cart:<userId>`
- Uses Redis **Hash** structure
- TTL is applied to automatically expire inactive carts

### 2. Menu Caching

- Menu items are cached to reduce database load
- Frequently accessed menu lists are served directly from Redis
- Cache is invalidated on menu updates

---

## Benefits

- **High performance** and low latency
- **Reduces database load**
- **Automatic expiration** using TTL
- Suitable for temporary and frequently updated data

---

## Summary

Redis improves system responsiveness by handling shopping cart data and menu caching efficiently, making it ideal for high-traffic scenarios.