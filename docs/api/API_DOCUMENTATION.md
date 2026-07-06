# API Documentation

## Base URL

All external requests are intended to go through the API Gateway:

```text
http://localhost:8080
```

Individual services also expose their own container/local ports during Docker Compose development, but the gateway is the main entry point.

## Authentication

Authentication is JWT-based.

1. Register or log in through `user-service`.
2. Use the returned token as a bearer token.
3. The gateway validates the token and propagates user context headers to downstream services.

Header format:

```text
Authorization: Bearer <JWT_TOKEN>
```

Common roles used by the services:

- `CUSTOMER`
- `VENDOR`
- `ADMIN`

Some service methods are protected with `@PreAuthorize`; several internal endpoints are also used by service-to-service calls.

## Common Response Format

Most controller APIs return the shared `ApiResponse` shape:

```json
{
  "success": true,
  "message": "Success",
  "data": {},
  "code": 200
}
```

Some internal/simple service endpoints currently return DTOs directly or return no body. Those differences are noted below where relevant.

## Auth APIs

### Register

```http
POST /api/auth/register
```

Request:

```json
{
  "username": "john",
  "password": "change-me",
  "email": "john@example.com",
  "phone": "555-0100",
  "role": "CUSTOMER"
}
```

### Login

```http
POST /api/auth/login
```

Request:

```json
{
  "username": "john",
  "password": "change-me"
}
```

Response data:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

## User APIs

### Get User

```http
GET /api/users/{userId}
```

### List Users

```http
GET /api/users
```

### Update User

```http
PUT /api/users/{userId}
```

Request:

```json
{
  "username": "john",
  "email": "john@example.com",
  "phone": "555-0101",
  "role": "CUSTOMER"
}
```

### Delete User

```http
DELETE /api/users/{userId}
```

## Catalog APIs

### List Active Products

```http
GET /api/catalog/products
```

### Get Product by ID

```http
GET /api/catalog/products/{id}
```

### Get Product by SKU

```http
GET /api/catalog/products/sku/{sku}
```

### Get Products by Category

```http
GET /api/catalog/products/category?category=makeup
```

### Create Product

```http
POST /api/catalog/products
```

Access:

- `ADMIN`
- `VENDOR`

Request:

```json
{
  "sku": "SKU-1001",
  "productName": "Hydrating Serum",
  "brand": "Demo Brand",
  "category": "skincare",
  "description": "Lightweight daily serum",
  "price": 29.99,
  "imageUrl": "https://example.com/product.png",
  "active": true
}
```

### Update Product

```http
PUT /api/catalog/products/{id}
```

Access:

- `ADMIN`
- `VENDOR`

### Delete Product

```http
DELETE /api/catalog/products/{id}
```

Access:

- `ADMIN`

## Cart APIs

Cart APIs use the authenticated user context propagated by the gateway.

### Get Cart

```http
GET /api/cart
```

### Add Item to Cart

```http
POST /api/cart/items
```

Request:

```json
{
  "productId": 1,
  "quantity": 2
}
```

The service enriches the cart item with product SKU, name, brand, and price from `catalog-service`.

### Update Item Quantity

```http
PUT /api/cart/items/{itemId}?qty=3
```

### Remove Item

```http
DELETE /api/cart/items/{itemId}
```

### Clear Cart

```http
DELETE /api/cart/clear
```

## Order APIs

### Create Order

```http
POST /api/orders
```

Access:

- `CUSTOMER`
- `ADMIN`

Request:

```json
{
  "promotionCode": "SUMMER10",
  "source": "WEB"
}
```

Notes:

- `userId` is populated from the authenticated request context.
- `source` can be `WEB`, `MOBILE`, `STORE`, or `MARKETPLACE`.
- Order data is stored through ShardingSphere.
- Inventory reservation is performed during order creation.
- The cart is cleared after order creation.

### Pay Order

```http
POST /api/orders/{orderId}/pay
```

Access:

- `CUSTOMER`
- `ADMIN`

Notes:

- `order-service` calls `payment-service`.
- Successful payment commits inventory and publishes an `ORDER_PAID` event.
- Failed payment releases inventory and cancels the order.

### Cancel Order

```http
POST /api/orders/{orderId}/cancel
```

Access:

- `CUSTOMER`

Only `CREATED` orders can be cancelled by this endpoint.

### Get Order

```http
GET /api/orders/{orderId}
```

Access:

- `CUSTOMER`
- `ADMIN`

### Get My Orders

```http
GET /api/orders/my?page=0&size=10
```

Access:

- `CUSTOMER`

### Admin List/Search Orders

```http
GET /api/orders?page=0&size=10
GET /api/orders?status=PAID&page=0&size=10
```

Access:

- `ADMIN`

Supported order statuses:

- `CREATED`
- `PAID`
- `PAYMENT_FAILED`
- `PROCESSING`
- `SHIPPED`
- `DELIVERED`
- `CANCELLED`

## Inventory APIs

These endpoints are used by the order workflow and can also be useful for local testing.

### Get Inventory by SKU

```http
GET /api/inventory/{sku}
```

### Reserve Inventory

```http
POST /api/inventory/reserve
```

Request:

```json
{
  "orderId": 1001,
  "items": [
    {
      "sku": "SKU-1001",
      "quantity": 2
    }
  ]
}
```

### Commit Inventory

```http
POST /api/inventory/commit/{orderId}
```

### Release Inventory

```http
POST /api/inventory/release/{orderId}
```

## Payment APIs

### Authorize Payment

```http
POST /api/payments/authorize
```

Access:

- `CUSTOMER`

Request:

```json
{
  "orderId": 1001,
  "userId": 1,
  "amount": 49.99,
  "paymentMethod": "CREDIT_CARD",
  "idempotencyKey": "pay-1001"
}
```

### Simulate Authorization Failure

```http
POST /api/payments/authorize/fail
```

Access:

- `ADMIN`

This endpoint exists for Saga/failure-path testing.

### Refund Payment

```http
POST /api/payments/{paymentId}/refund
```

Access:

- `ADMIN`

### Mark Payment Failed

```http
POST /api/payments/{paymentId}/fail
```

Access:

- `ADMIN`

## Ledger APIs

### Get Ledger Entries by Order

```http
GET /api/ledger/orders/{orderId}
```

Access:

- `CUSTOMER`
- `ADMIN`

### Get Ledger Entries by Payment

```http
GET /api/ledger/payments/{paymentId}
```

Access:

- `CUSTOMER`
- `ADMIN`

## Fulfillment APIs

### Get Fulfillment by Order

```http
GET /api/fulfillment/orders/{orderId}
```

Access:

- `CUSTOMER`
- `ADMIN`

### Update Fulfillment Status

```http
PATCH /api/fulfillment/orders/{orderId}/status
```

Access:

- `VENDOR`
- `ADMIN`
- `CUSTOMER`

Request:

```json
{
  "status": "SHIPPED"
}
```

Supported fulfillment statuses:

- `CREATED`
- `PROCESSING`
- `SHIPPED`
- `DELIVERED`
- `CANCELLED`

Updating fulfillment status publishes a `fulfillment-events` Kafka message consumed by `order-service`.

## Promotion APIs

### Validate Promotion

```http
GET /api/promotions/{code}?source=WEB
```

Supported sources:

- `WEB`
- `MOBILE`
- `STORE`
- `MARKETPLACE`

### Create Promotion

```http
POST /api/promotions
```

Access:

- `ADMIN`
- `VENDOR`
- `CUSTOMER`

Request:

```json
{
  "code": "SUMMER10",
  "discountPercentage": 10,
  "active": true,
  "startTime": "2026-07-01T00:00:00",
  "endTime": "2026-08-01T00:00:00",
  "allowedSources": ["WEB", "MOBILE"]
}
```

### Update Promotion

```http
PUT /api/promotions/{id}
```

Access:

- `ADMIN`
- `VENDOR`

### Delete Promotion

```http
DELETE /api/promotions/{id}
```

Access:

- `ADMIN`
- `VENDOR`

## Kafka Side Effects

Some APIs trigger asynchronous Kafka flows:

| API Action                    | Event Topic          | Consumer                          |
|-------------------------------|----------------------|-----------------------------------|
| Pay order successfully        | `order-events`       | `fulfillment-service`             |
| Authorize/refund/fail payment | `payment-events`     | `ledger-service`, `order-service` |
| Update fulfillment status     | `fulfillment-events` | `order-service`                   |

## Error Handling

The common exception handler maps application errors into API error responses. Typical HTTP outcomes include:

- `400 Bad Request` for validation or illegal state transitions
- `401 Unauthorized` for missing/invalid authentication
- `403 Forbidden` for insufficient role permissions
- `404 Not Found` for missing resources
- `503 Service Unavailable` for downstream service failures

Most errors use the shared response shape with `success=false`.
