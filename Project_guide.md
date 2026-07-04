Project Architecture

Microservices:
- Gateway
- Eureka
- cart
- catalog
- Order
- Inventory
- Payment
- Ledger
- promotion
- Fulfillment
- user

Messaging
Kafka

Database
MySQL
Redis

Security
JWT

Monitoring
Micrometer
Prometheus
Grafana

Micrometer / Actuator names:
order_creation
payment_authorized
payment_failed
inventory_reserved
inventory_business_release
inventory_expired_release
fulfillment_creation
Prometheus names:
order_creation_total
payment_authorized_total
payment_failed_total
inventory_reserved_total
inventory_business_release_total
inventory_expired_release_total
fulfillment_creation_total

Coding Convention
Constructor Injection
No Field Injection
Service Layer Transaction
...