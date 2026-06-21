# Online Ordering System – API Documentation

## Base URL
All requests go through the API Gateway:
```
http://localhost:8080
```

---

## Authentication & Authorization

### Authentication
* Uses JWT (Bearer Token)
* Token is obtained via `/api/auth/login`
* Token must be included in the request header
```
Authorization: Bearer <JWT_TOKEN>
```

### Roles
* `ROLE_CUSTOMER`
* `ROLE_VENDOR`
* `ROLE_ADMIN`

Role-based access control is enforced at the service level.

---

## Common Response Format

All APIs return a unified response structure:
```json
{
  "success": true,
  "code": 200,
  "message": "Success",
  "data": {}
}
```

Error example:
```json
{
  "success": false,
  "code": 404,
  "message": "Resource not found",
  "data": null
}
```

---

## Auth Service (User Service)

### Register User

**Endpoint**
```
POST /api/auth/register
```

**Request Body**
```json
{
  "username": "john",
  "password": "password123",
  "role": "ROLE_CUSTOMER"
}
```

### Login

**Endpoint**
```
POST /api/auth/login
```

**Request Body**
```json
{
  "username": "john",
  "password": "password123"
}
```

**Response**
```json
{
  "success": true,
  "code": 200,
  "message": "Success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

---

## User Service

### Get User by ID

**Endpoint**
```
GET /api/users/{id}
```

**Access**
* ADMIN
* User himself

### Get All Users

**Endpoint**
```
GET /api/users
```

**Access**
* ADMIN only

---

## Menu Service

### Get All Available Menu Items

**Endpoint**
```
GET /api/menu/items
```

**Access**
* Public (no authentication required)

### Get Menu Items by Category

**Endpoint**
```
GET /api/menu/category?category=Food
```

### Create Menu Item

**Endpoint**
```
POST /api/menu/items
```

**Access**
* `ROLE_VENDOR`
* `ROLE_ADMIN`

**Request Body**
```json
{
  "name": "Burger",
  "description": "Beef burger",
  "price": 8.99,
  "category": "Food",
  "imageUrl": "http://image.url",
  "available": true
}
```

### Update Menu Item

**Endpoint**
```
PUT /api/menu/items/{id}
```

**Access**
* `ROLE_VENDOR`
* `ROLE_ADMIN`

### Delete Menu Item

**Endpoint**
```
DELETE /api/menu/items/{id}
```

**Access**
* `ROLE_VENDOR`
* `ROLE_ADMIN`

---

## Cart Service (Redis)

### Get Current User Cart

**Endpoint**
```
GET /api/cart
```

**Headers**
```
Authorization: Bearer <JWT_TOKEN>
```

### Add Item to Cart

**Endpoint**
```
POST /api/cart/items
```

**Request Body**
```json
{
  "menuItemId": 1,
  "quantity": 2
}
```

### Remove Item from Cart

**Endpoint**
```
DELETE /api/cart/items/{menuItemId}
```

### Clear Cart

**Endpoint**
```
DELETE /api/cart/clear
```

---

## Order Service

### Create Order

**Endpoint**
```
POST /api/orders
```


**Description**
* Reads cart data from Redis
* Creates order in MySQL (sharded)
* Publishes order event to message queue

### Pay Order

**Endpoint**
```
POST /api/orders/{orderId}/pay
```


### Cancel Order

**Endpoint**
```
POST /api/orders/{orderId}/cancel
```

**Access**
* `(only if order is not PAID)` 

### Get Order by ID

**Endpoint**
```
GET /api/orders/{orderId}
```

**Access**
* Order owner
* `ROLE_ADMIN`

### Get Current User Orders

**Endpoint**
```
GET /api/orders/my
```


### Admin – Search Orders

**Endpoints**
```
GET /api/orders
GET /api/orders?status=PAID
```

**Access**
* `ROLE_ADMIN`

---

## Error Handling Summary

* Validation errors → `400 Bad Request`
* Unauthorized access → `401 Unauthorized`
* Forbidden access → `403 Forbidden`
* Resource not found → `404`
* Downstream service unavailable → `503 Service Unavailable`

All errors return unified `ApiResponse` format.