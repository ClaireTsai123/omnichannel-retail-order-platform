# Database Sharding

## Purpose

Database sharding is used to scale order storage and distribute write load across multiple databases.

---

## Technology

- **Apache ShardingSphere**
- **MySQL**

---

## Sharding Strategy

- Orders are distributed across multiple databases:
   - `order_db_0`
   - `order_db_1`
- **Sharding key:** `order_id`
- **Sharding algorithm:** Inline sharding

---

## How It Works

1. Order Service generates a unique order ID
2. ShardingSphere routes the order to a specific database
3. Application accesses orders using a single logical data source

---

## Benefits

- **Horizontal scalability**
- **Improved write performance**
- **Transparent** to application code
- Suitable for high-volume order data

---

## Summary

Sharding enables the system to scale order data storage efficiently while keeping database access simple and consistent.