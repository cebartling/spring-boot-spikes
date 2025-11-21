# Shopping Cart REST API Documentation

This document describes the RESTful APIs for the Shopping Cart domain.

## Base URL

```
http://localhost:8080/api/v1
```

## API Endpoints

### Shopping Cart Controller

#### Create Cart
```
POST /carts
Content-Type: application/json

Request Body:
{
  "sessionId": "session-123",
  "userId": "user-456",  // optional
  "expiresAt": "2025-12-01T00:00:00Z"  // optional
}

Response: 201 Created
{
  "id": 1,
  "cartUuid": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "user-456",
  "sessionId": "session-123",
  "status": "ACTIVE",
  "currencyCode": "USD",
  "subtotalCents": 0,
  "taxAmountCents": 0,
  "discountAmountCents": 0,
  "totalAmountCents": 0,
  "itemCount": 0,
  "metadata": null,
  "createdAt": "2025-11-20T22:00:00Z",
  "updatedAt": "2025-11-20T22:00:00Z",
  "expiresAt": "2025-12-01T00:00:00Z",
  "convertedAt": null
}
```

#### Get Cart by ID
```
GET /carts/{cartId}

Response: 200 OK
{
  "id": 1,
  "cartUuid": "550e8400-e29b-41d4-a716-446655440000",
  ...
}
```

#### Get Cart by UUID
```
GET /carts/uuid/{cartUuid}

Response: 200 OK
```

#### Get or Create Cart for Session
```
GET /carts/session/{sessionId}?userId=user-456

Response: 200 OK
```

#### Get Cart by Session
```
GET /carts/session/{sessionId}/current

Response: 200 OK
```

#### Get Carts by User
```
GET /carts/user/{userId}

Response: 200 OK
[
  { cart object },
  ...
]
```

#### Get Carts by Status
```
GET /carts/status/{status}
// status: ACTIVE, ABANDONED, CONVERTED, EXPIRED

Response: 200 OK
[
  { cart object },
  ...
]
```

#### Associate Cart with User
```
PUT /carts/{cartId}/user
Content-Type: application/json

Request Body:
{
  "userId": "user-456"
}

Response: 200 OK
```

#### Update Cart Expiration
```
PUT /carts/{cartId}/expiration
Content-Type: application/json

Request Body:
{
  "expiresAt": "2025-12-01T00:00:00Z"
}

Response: 200 OK
```

#### Abandon Cart
```
POST /carts/{cartId}/abandon

Response: 200 OK
```

#### Convert Cart
```
POST /carts/{cartId}/convert

Response: 200 OK
```

#### Expire Cart
```
POST /carts/{cartId}/expire

Response: 200 OK
```

#### Restore Cart
```
POST /carts/{cartId}/restore

Response: 200 OK
```

#### Get Expired Carts
```
GET /carts/expired

Response: 200 OK
[
  { cart object },
  ...
]
```

#### Get Abandoned Carts
```
GET /carts/abandoned?hoursInactive=24

Response: 200 OK
[
  { cart object },
  ...
]
```

#### Process Expired Carts
```
POST /carts/process-expired

Response: 200 OK
5  // number of carts processed
```

#### Process Abandoned Carts
```
POST /carts/process-abandoned?hoursInactive=24

Response: 200 OK
3  // number of carts processed
```

#### Get Carts with Items
```
GET /carts/with-items?status=ACTIVE

Response: 200 OK
[
  { cart object },
  ...
]
```

#### Get Empty Carts
```
GET /carts/empty

Response: 200 OK
[
  { cart object },
  ...
]
```

#### Count Carts by Status
```
GET /carts/count/{status}

Response: 200 OK
42
```

#### Get Cart Statistics
```
GET /carts/statistics

Response: 200 OK
{
  "totalCarts": 100,
  "activeCarts": 60,
  "abandonedCarts": 20,
  "convertedCarts": 15,
  "expiredCarts": 5
}
```

#### Delete Cart
```
DELETE /carts/{cartId}

Response: 204 No Content
```

---

### Cart Item Controller

#### Get All Cart Items
```
GET /carts/{cartId}/items

Response: 200 OK
[
  {
    "id": 1,
    "cartId": 1,
    "productId": "550e8400-e29b-41d4-a716-446655440000",
    "sku": "PROD-001",
    "productName": "Sample Product",
    "quantity": 2,
    "unitPriceCents": 9999,
    "lineTotalCents": 19998,
    "discountAmountCents": 0,
    "metadata": null,
    "addedAt": "2025-11-20T22:00:00Z",
    "updatedAt": "2025-11-20T22:00:00Z"
  },
  ...
]
```

#### Get Specific Cart Item
```
GET /carts/{cartId}/items/{productId}

Response: 200 OK
```

#### Add Item to Cart
```
POST /carts/{cartId}/items
Content-Type: application/json

Request Body:
{
  "productId": "550e8400-e29b-41d4-a716-446655440000",
  "quantity": 2
}

Response: 201 Created
```

#### Update Item Quantity
```
PUT /carts/{cartId}/items/{productId}/quantity
Content-Type: application/json

Request Body:
{
  "quantity": 5
}

Response: 200 OK
```

#### Apply Item Discount
```
PUT /carts/{cartId}/items/{productId}/discount
Content-Type: application/json

Request Body:
{
  "discountAmountCents": 1000
}

Response: 200 OK
```

#### Update Item Metadata
```
PUT /carts/{cartId}/items/{productId}/metadata
Content-Type: application/json

Request Body:
{
  "metadata": "{\"color\": \"red\", \"size\": \"large\"}"
}

Response: 200 OK
```

#### Remove Item from Cart
```
DELETE /carts/{cartId}/items/{productId}

Response: 204 No Content
```

#### Clear Cart
```
DELETE /carts/{cartId}/items

Response: 204 No Content
```

#### Get Cart Totals
```
GET /carts/{cartId}/items/totals

Response: 200 OK
{
  "subtotalCents": 19998,
  "taxAmountCents": 0,
  "discountAmountCents": 0,
  "totalAmountCents": 19998,
  "itemCount": 2
}
```

#### Count Cart Items
```
GET /carts/{cartId}/items/count

Response: 200 OK
2
```

#### Get Discounted Items
```
GET /carts/{cartId}/items/discounted

Response: 200 OK
[
  { cart item object },
  ...
]
```

#### Get High Value Items
```
GET /carts/{cartId}/items/high-value?minPriceCents=10000

Response: 200 OK
[
  { cart item object },
  ...
]
```

#### Get Bulk Items
```
GET /carts/{cartId}/items/bulk?minQuantity=5

Response: 200 OK
[
  { cart item object },
  ...
]
```

#### Validate Item Availability
```
GET /carts/{cartId}/items/{productId}/validate

Response: 200 OK
{
  "productId": "550e8400-e29b-41d4-a716-446655440000",
  "available": true,
  "reason": null
}
```

#### Validate All Cart Items
```
GET /carts/{cartId}/items/validate

Response: 200 OK
{
  "cartId": 1,
  "items": [
    {
      "productId": "550e8400-e29b-41d4-a716-446655440000",
      "available": true,
      "reason": null
    },
    {
      "productId": "660e8400-e29b-41d4-a716-446655440001",
      "available": false,
      "reason": "Product is not available or out of stock"
    }
  ],
  "allItemsValid": false
}
```

---

### Cart State History Controller

#### Get Cart History
```
GET /carts/{cartId}/history

Response: 200 OK
[
  {
    "id": 1,
    "cartId": 1,
    "eventType": "CREATED",
    "previousStatus": null,
    "newStatus": null,
    "eventData": null,
    "createdAt": "2025-11-20T22:00:00Z"
  },
  ...
]
```

#### Get Recent Cart Events
```
GET /carts/{cartId}/history/recent?hoursBack=24

Response: 200 OK
[
  { history object },
  ...
]
```

#### Get Events by Type
```
GET /carts/{cartId}/history/type/{eventType}
// eventType: CREATED, ITEM_ADDED, ITEM_REMOVED, etc.

Response: 200 OK
[
  { history object },
  ...
]
```

#### Get Most Recent Event
```
GET /carts/{cartId}/history/latest

Response: 200 OK
{
  "id": 5,
  "cartId": 1,
  "eventType": "ITEM_ADDED",
  ...
}
```

#### Count Events by Type
```
GET /carts/{cartId}/history/count/{eventType}

Response: 200 OK
3
```

#### Count Total Events
```
GET /carts/{cartId}/history/count

Response: 200 OK
10
```

#### Get Cart Activity Summary
```
GET /carts/{cartId}/history/summary

Response: 200 OK
{
  "cartId": 1,
  "eventCounts": {
    "CREATED": 1,
    "ITEM_ADDED": 3,
    "ITEM_REMOVED": 1,
    "ITEM_UPDATED": 2
  },
  "totalEvents": 7
}
```

---

### Cart Analytics Controller

#### Get Events in Date Range
```
GET /analytics/carts/events?startDate=2025-11-01T00:00:00Z&endDate=2025-11-30T23:59:59Z

Response: 200 OK
[
  { history object },
  ...
]
```

#### Get Conversion Events
```
GET /analytics/carts/conversions?startDate=2025-11-01T00:00:00Z&endDate=2025-11-30T23:59:59Z

Response: 200 OK
[
  { history object },
  ...
]
```

#### Get Abandonment Events
```
GET /analytics/carts/abandonments?startDate=2025-11-01T00:00:00Z&endDate=2025-11-30T23:59:59Z

Response: 200 OK
[
  { history object },
  ...
]
```

#### Get Conversion Rate
```
GET /analytics/carts/conversion-rate?startDate=2025-11-01T00:00:00Z&endDate=2025-11-30T23:59:59Z

Response: 200 OK
{
  "startDate": "2025-11-01T00:00:00Z",
  "endDate": "2025-11-30T23:59:59Z",
  "conversionRate": 15.5,
  "totalCreated": 100,
  "totalConverted": 15
}
```

#### Get Abandonment Rate
```
GET /analytics/carts/abandonment-rate?startDate=2025-11-01T00:00:00Z&endDate=2025-11-30T23:59:59Z

Response: 200 OK
{
  "startDate": "2025-11-01T00:00:00Z",
  "endDate": "2025-11-30T23:59:59Z",
  "abandonmentRate": 25.0,
  "totalCreated": 100,
  "totalAbandoned": 25
}
```

---

## Error Responses

All errors follow this format:

```json
{
  "timestamp": "2025-11-20T22:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Cart not found with id: 123",
  "path": "/api/v1/carts/123"
}
```

### HTTP Status Codes

- `200 OK` - Request succeeded
- `201 Created` - Resource created successfully
- `204 No Content` - Request succeeded with no response body
- `400 Bad Request` - Invalid request (validation error)
- `404 Not Found` - Resource not found
- `409 Conflict` - Invalid state transition
- `500 Internal Server Error` - Unexpected server error

---

## Testing the API

### Using cURL

Create a cart:
```bash
curl -X POST http://localhost:8080/api/v1/carts \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "test-session-123"}'
```

Add item to cart:
```bash
curl -X POST http://localhost:8080/api/v1/carts/1/items \
  -H "Content-Type: application/json" \
  -d '{"productId": "550e8400-e29b-41d4-a716-446655440000", "quantity": 2}'
```

Get cart:
```bash
curl http://localhost:8080/api/v1/carts/1
```

### Using HTTPie

Create a cart:
```bash
http POST :8080/api/v1/carts sessionId=test-session-123
```

Add item to cart:
```bash
http POST :8080/api/v1/carts/1/items \
  productId=550e8400-e29b-41d4-a716-446655440000 \
  quantity:=2
```

Get cart:
```bash
http :8080/api/v1/carts/1
```

---

## Notes

- All monetary values are represented in cents (integers) for precision
- All timestamps are in ISO-8601 format with timezone (OffsetDateTime)
- UUIDs are used for cart and product identification
- Cart status can be: ACTIVE, ABANDONED, CONVERTED, EXPIRED
- Event types include: CREATED, ITEM_ADDED, ITEM_REMOVED, ITEM_UPDATED, ABANDONED, CONVERTED, EXPIRED
