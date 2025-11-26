# Feature: Inventory Integration (CQRS Architecture)

## Overview

As a business, we need a robust inventory management system that tracks stock levels across multiple locations, supports reservations for pending orders, handles backorders, and provides real-time availability information to customers. The inventory system must integrate seamlessly with the product catalog and variant systems while maintaining eventual consistency through event-driven communication. Using CQRS with Event Sourcing provides a complete audit trail of all inventory movements, enables point-in-time stock queries, and supports complex inventory analytics.

## Architecture Principles

### CQRS Separation
- **Command Side**: Manages inventory adjustments, reservations, transfers, and replenishment through domain aggregates
- **Query Side**: Provides fast availability lookups, stock level queries, and inventory analytics through optimized read models
- **Event Store**: Captures every inventory movement for audit trails, reconciliation, and historical analysis

### Inventory Design Patterns
- **Stock Keeping Unit (SKU)**: Each variant has a unique SKU that maps to inventory records
- **Location-Based Inventory**: Stock is tracked per location (warehouse, store, virtual)
- **Reservation Pattern**: Stock is reserved during checkout, committed on order completion
- **Eventual Consistency**: Inventory updates propagate asynchronously to read models and dependent systems

### Concurrency Control
- Optimistic locking prevents overselling
- Reservation system handles concurrent checkout scenarios
- Idempotent operations support safe retries

## Scope

### Phase 1: Core Inventory Management
- Stock level tracking per SKU per location
- Inventory adjustments (receive, adjust, damage, loss)
- Stock reservations for orders
- Real-time availability queries
- Low stock alerts

### Phase 2: Advanced Inventory (Future)
- Multi-location transfers
- Inventory allocation rules
- Backorder management
- Inventory forecasting

### Phase 3: Warehouse Operations (Future)
- Bin/shelf location tracking
- Pick, pack, ship workflows
- Cycle counting
- Lot/batch tracking
- Serial number tracking

## Domain Model

### Inventory Location
- Location ID (UUID)
- Name (e.g., "Main Warehouse", "Store #42")
- Location Type (WAREHOUSE, STORE, VIRTUAL, DROP_SHIP)
- Address (for physical locations)
- Status (ACTIVE, INACTIVE, CLOSED)
- Is Default (for receiving)
- Priority (for allocation)

### Inventory Item (Aggregate Root)
- Inventory ID (UUID)
- SKU (unique identifier, links to product variant)
- Location ID (UUID)
- Quantity On Hand (physical stock)
- Quantity Reserved (held for orders)
- Quantity Available (on hand - reserved)
- Quantity On Order (incoming from suppliers)
- Reorder Point (trigger for replenishment)
- Reorder Quantity (default order amount)
- Safety Stock (minimum buffer)
- Status (ACTIVE, DISCONTINUED, BLOCKED)
- Last Counted At (physical inventory date)
- Version (optimistic concurrency)

### Inventory Reservation
- Reservation ID (UUID)
- SKU
- Location ID
- Quantity Reserved
- Reference Type (ORDER, CART, HOLD)
- Reference ID (order ID, cart ID, etc.)
- Reserved At
- Expires At (for cart reservations)
- Status (ACTIVE, COMMITTED, RELEASED, EXPIRED)

### Inventory Movement
- Movement ID (UUID)
- SKU
- Location ID (source or destination)
- Movement Type (RECEIVE, SELL, ADJUST, TRANSFER_IN, TRANSFER_OUT, DAMAGE, LOSS, RETURN, COUNT)
- Quantity (positive or negative)
- Reference Type (PO, ORDER, ADJUSTMENT, TRANSFER, COUNT)
- Reference ID
- Reason Code
- Notes
- Performed By (user ID)
- Performed At

### Domain Events
- `InventoryLocationCreated`
- `InventoryLocationUpdated`
- `InventoryItemCreated`
- `InventoryReceived`
- `InventoryAdjusted`
- `InventorySold`
- `InventoryReturned`
- `InventoryDamaged`
- `InventoryLost`
- `InventoryTransferredOut`
- `InventoryTransferredIn`
- `InventoryCounted`
- `InventoryReservationCreated`
- `InventoryReservationCommitted`
- `InventoryReservationReleased`
- `InventoryReservationExpired`
- `LowStockAlertTriggered`
- `OutOfStockAlertTriggered`
- `RestockAlertTriggered`
- `InventoryReconciled`

## Acceptance Criteria

### AC1: Inventory Location Command Model

**Implementation Plan:** [AC1 - Inventory Location Command Model](../plans/inventory-integration/AC1-inventory-location-command-model.md)

- An `InventoryLocation` aggregate exists in the `command_model` schema
- The aggregate supports location types: WAREHOUSE, STORE, VIRTUAL, DROP_SHIP
- The aggregate enforces business invariants:
  - Name is required and unique
  - Only one location can be default for receiving
  - Location cannot be closed if it has non-zero inventory
  - Priority must be positive integer (lower = higher priority)
- Location status transitions are validated:
  - ACTIVE → INACTIVE (can be reactivated)
  - ACTIVE → CLOSED (terminal, requires zero inventory)
  - INACTIVE → ACTIVE
  - INACTIVE → CLOSED
- The aggregate generates domain events for all state changes
- All operations return `Mono<T>` for reactive compatibility

### AC2: Inventory Item Command Model

**Implementation Plan:** [AC2 - Inventory Item Command Model](../plans/inventory-integration/AC2-inventory-item-command-model.md)

- An `InventoryItem` aggregate root exists in the `command_model` schema
- Inventory is tracked per SKU per location (composite identity)
- The aggregate maintains:
  - Quantity on hand (physical stock count)
  - Quantity reserved (held for orders)
  - Quantity available (calculated: on hand - reserved)
  - Quantity on order (expected from suppliers)
- The aggregate enforces business invariants:
  - Quantity on hand cannot be negative
  - Quantity reserved cannot exceed quantity on hand
  - Quantity available is always >= 0
  - SKU must reference existing product variant
  - Location must be active
- Optimistic concurrency using version numbers
- All operations return `Mono<T>` for reactive compatibility

### AC3: Inventory Adjustment Commands

**Implementation Plan:** [AC3 - Inventory Adjustment Commands](../plans/inventory-integration/AC3-inventory-adjustment-commands.md)

- `ReceiveInventoryCommand` adds stock from supplier/transfer
  - Increases quantity on hand
  - Optionally decreases quantity on order
  - Requires reference (PO number, transfer ID)
  - Emits `InventoryReceived` event
- `AdjustInventoryCommand` corrects stock discrepancies
  - Can increase or decrease quantity on hand
  - Requires reason code and notes
  - Emits `InventoryAdjusted` event
- `RecordDamageCommand` reduces stock due to damage
  - Decreases quantity on hand
  - Requires damage reason and notes
  - Emits `InventoryDamaged` event
- `RecordLossCommand` reduces stock due to loss/theft
  - Decreases quantity on hand
  - Requires loss reason and notes
  - Emits `InventoryLost` event
- `RecordSaleCommand` reduces stock for completed sale
  - Converts reserved to sold (decreases on hand)
  - Requires order reference
  - Emits `InventorySold` event
- `RecordReturnCommand` increases stock from customer return
  - Increases quantity on hand
  - Requires return authorization reference
  - Emits `InventoryReturned` event
- All commands validate sufficient available quantity
- All commands record performing user and timestamp

### AC4: Inventory Reservation System

**Implementation Plan:** [AC4 - Inventory Reservation System](../plans/inventory-integration/AC4-inventory-reservation-system.md)

- `CreateReservationCommand` reserves stock for an order/cart
  - Validates sufficient available quantity
  - Increases quantity reserved
  - Decreases quantity available
  - Sets expiration for cart reservations (configurable, default: 15 minutes)
  - Emits `InventoryReservationCreated` event
- `CommitReservationCommand` converts reservation to sale
  - Decreases quantity on hand
  - Decreases quantity reserved
  - Emits `InventoryReservationCommitted` event
  - Emits `InventorySold` event
- `ReleaseReservationCommand` cancels reservation
  - Decreases quantity reserved
  - Increases quantity available
  - Emits `InventoryReservationReleased` event
- `ExtendReservationCommand` extends expiration time
  - Updates expiration timestamp
  - Maximum extension limit (configurable)
- Reservations support multiple reference types:
  - ORDER: Firm reservation for placed order
  - CART: Soft reservation for shopping cart
  - HOLD: Manual hold for customer service
- Reservation lookup by reference ID supported

### AC5: Reservation Expiration Service

**Implementation Plan:** [AC5 - Reservation Expiration Service](../plans/inventory-integration/AC5-reservation-expiration-service.md)

- Scheduled service monitors reservation expiration
- Expired reservations are automatically released
- Expiration check runs at configurable interval (default: 1 minute)
- Service processes expirations in batches for efficiency
- `InventoryReservationExpired` event emitted for each expiration
- Service is resilient to restarts (processes all overdue expirations)
- Distributed locking prevents duplicate processing in clustered environment
- Expiration processing is idempotent
- Metrics track expiration counts and latency

### AC6: Inventory Event Store

**Implementation Plan:** [AC6 - Inventory Event Store](../plans/inventory-integration/AC6-inventory-event-store.md)

- All inventory events persisted to `event_store` schema
- Events capture complete movement context:
  - Previous quantity
  - New quantity
  - Change amount
  - Reference information
  - User and timestamp
- Events support querying by:
  - SKU (all movements for an item)
  - Location (all movements at location)
  - Movement type (all receives, all sales, etc.)
  - Time range (movements between dates)
  - Reference (all movements for an order)
- Events enable reconstruction of inventory state at any point in time
- Events support reconciliation between physical and system counts
- Correlation IDs link related events across aggregates

### AC7: Inventory Read Model

**Implementation Plan:** [AC7 - Inventory Read Model](../plans/inventory-integration/AC7-inventory-read-model.md)

- Denormalized read model in `read_model` schema
- Read model includes:
  - SKU, Product name, Variant details (denormalized)
  - Location ID, Location name
  - Quantity on hand, reserved, available, on order
  - Reorder point, reorder quantity, safety stock
  - Status (ACTIVE, DISCONTINUED, BLOCKED)
  - Low stock flag, out of stock flag
  - Last movement timestamp
  - Last counted timestamp
- Read model supports efficient queries:
  - By SKU (across all locations)
  - By location (all items at location)
  - By availability (in stock, low stock, out of stock)
  - By status
- Aggregated views:
  - Total quantity across all locations per SKU
  - Location summary (total items, value, status breakdown)
- Read model updated from domain events
- Updates are idempotent for event replay

### AC8: Availability Query Service

**Implementation Plan:** [AC8 - Availability Query Service](../plans/inventory-integration/AC8-availability-query-service.md)

- Query returns availability for single SKU
- Query returns availability for multiple SKUs (batch)
- Query returns availability by location or aggregated
- Availability response includes:
  ```json
  {
    "sku": "PROD-001-RD-XL",
    "totalAvailable": 150,
    "locations": [
      {
        "locationId": "uuid",
        "locationName": "Main Warehouse",
        "available": 100,
        "onHand": 120,
        "reserved": 20
      }
    ],
    "status": "IN_STOCK",
    "backorderAvailable": false,
    "estimatedRestockDate": null
  }
  ```
- Status calculation:
  - IN_STOCK: available > safety stock
  - LOW_STOCK: 0 < available <= safety stock
  - OUT_OF_STOCK: available = 0
  - BACKORDER: available = 0 but backorder enabled
- Query supports location filtering
- Query supports minimum quantity check (for cart validation)
- All queries return `Mono<T>` or `Flux<T>`
- Responses are cacheable with appropriate TTL

### AC9: Stock Alert System

**Implementation Plan:** [AC9 - Stock Alert System](../plans/inventory-integration/AC9-stock-alert-system.md)

- Low stock alert triggered when available <= reorder point
- Out of stock alert triggered when available = 0
- Restock alert triggered when previously out-of-stock item receives inventory
- Alerts are emitted as domain events
- Alert events include:
  - SKU and location
  - Current quantity
  - Threshold that triggered alert
  - Timestamp
- Alerts can trigger notifications (email, webhook, message queue)
- Alert configuration per SKU:
  - Enable/disable specific alert types
  - Custom thresholds
  - Notification channels
- Alert deduplication prevents repeated alerts for same condition
- Alert resolution tracked when condition clears

### AC10: Product Catalog Integration

**Implementation Plan:** [AC10 - Product Catalog Integration](../plans/inventory-integration/AC10-product-catalog-integration.md)

- Inventory subscribes to Product Catalog events
- `ProductVariantCreated` triggers inventory item creation (optional auto-create)
- `ProductVariantActivated` enables inventory tracking
- `ProductVariantDiscontinued` marks inventory as discontinued
- `ProductVariantDeleted` handles inventory cleanup
- Integration is event-driven (not synchronous)
- Event processing is idempotent
- Failed event processing is logged and retryable
- Inventory status updates propagate back to product read model:
  - Product read model includes `inStock` flag
  - Product read model includes `availableQuantity`
  - Updates occur within acceptable latency (< 1 second)

### AC11: Variant Integration

**Implementation Plan:** [AC11 - Variant Integration](../plans/inventory-integration/AC11-variant-integration.md)

- Inventory tracks stock at variant (SKU) level
- Variant selection service receives availability data
- Unavailable variants marked in selection response
- Bulk variant operations trigger inventory events:
  - Bulk variant discontinue → inventory status update
  - Bulk variant delete → inventory cleanup
- Inventory queries support variant attribute filtering:
  - "Show inventory for all RED variants"
  - "Show inventory for all XL size variants"
- Parent product availability aggregates variant availability:
  - Product is "in stock" if any variant is in stock
  - Product shows "limited availability" if some variants out of stock

### AC12: Inventory Movement History

**Implementation Plan:** [AC12 - Inventory Movement History](../plans/inventory-integration/AC12-inventory-movement-history.md)

- Query returns movement history for SKU
- Query returns movement history for location
- Query supports filtering by:
  - Movement type (RECEIVE, SELL, ADJUST, etc.)
  - Date range
  - Reference type and ID
  - User who performed movement
- Movement history response includes:
  ```json
  {
    "movements": [
      {
        "movementId": "uuid",
        "timestamp": "2024-01-15T10:30:00Z",
        "type": "RECEIVE",
        "quantity": 100,
        "previousOnHand": 50,
        "newOnHand": 150,
        "reference": {
          "type": "PURCHASE_ORDER",
          "id": "PO-2024-001"
        },
        "performedBy": "user-uuid",
        "notes": "Regular replenishment"
      }
    ],
    "summary": {
      "totalReceived": 500,
      "totalSold": 350,
      "totalAdjusted": -10,
      "netChange": 140
    }
  }
  ```
- Pagination support for large histories
- Export capability (CSV, JSON)

### AC13: Physical Inventory Count

**Implementation Plan:** [AC13 - Physical Inventory Count](../plans/inventory-integration/AC13-physical-inventory-count.md)

- `InitiateCountCommand` starts inventory count session
  - Can be full count or cycle count (subset of SKUs)
  - Freezes inventory movements during count (optional)
  - Records count start timestamp
- `RecordCountCommand` records counted quantity for SKU
  - Captures counted quantity
  - Calculates variance from system quantity
  - Supports multiple counts per SKU (for verification)
- `CompleteCountCommand` finalizes count session
  - Generates variance report
  - Optionally auto-adjusts inventory for variances
  - Requires approval for adjustments above threshold
  - Emits `InventoryCounted` events for each item
  - Emits `InventoryAdjusted` events for corrections
- Count session tracks:
  - Location being counted
  - SKUs included in count
  - Count progress (counted vs total)
  - Variance summary
  - Count performers
- Variance thresholds configurable per SKU or location

### AC14: Inventory REST API (Commands)

**Implementation Plan:** [AC14 - Inventory REST API Commands](../plans/inventory-integration/AC14-inventory-rest-api-commands.md)

**Location Management:**
- `POST /api/inventory/locations` creates location
- `PUT /api/inventory/locations/{id}` updates location
- `POST /api/inventory/locations/{id}/activate` activates location
- `POST /api/inventory/locations/{id}/deactivate` deactivates location
- `POST /api/inventory/locations/{id}/close` closes location

**Inventory Adjustments:**
- `POST /api/inventory/receive` receives inventory
  ```json
  {
    "sku": "PROD-001",
    "locationId": "uuid",
    "quantity": 100,
    "reference": {"type": "PURCHASE_ORDER", "id": "PO-001"},
    "notes": "Regular shipment"
  }
  ```
- `POST /api/inventory/adjust` adjusts inventory
- `POST /api/inventory/damage` records damage
- `POST /api/inventory/loss` records loss
- `POST /api/inventory/return` records return

**Reservations:**
- `POST /api/inventory/reservations` creates reservation
- `POST /api/inventory/reservations/{id}/commit` commits reservation
- `POST /api/inventory/reservations/{id}/release` releases reservation
- `POST /api/inventory/reservations/{id}/extend` extends reservation
- `DELETE /api/inventory/reservations/{id}` cancels reservation

**Inventory Counts:**
- `POST /api/inventory/counts` initiates count session
- `POST /api/inventory/counts/{id}/items` records count for item
- `POST /api/inventory/counts/{id}/complete` completes count
- `POST /api/inventory/counts/{id}/cancel` cancels count

**Bulk Operations:**
- `POST /api/inventory/bulk/receive` bulk receive
- `POST /api/inventory/bulk/adjust` bulk adjust
- `POST /api/inventory/bulk/reserve` bulk reserve

### AC15: Inventory REST API (Queries)

**Implementation Plan:** [AC15 - Inventory REST API Queries](../plans/inventory-integration/AC15-inventory-rest-api-queries.md)

**Location Queries:**
- `GET /api/inventory/locations` returns all locations
- `GET /api/inventory/locations/{id}` returns location details
- `GET /api/inventory/locations/{id}/summary` returns location inventory summary

**Inventory Queries:**
- `GET /api/inventory/items` returns inventory items (paginated)
  - Query params: sku, locationId, status, inStock, lowStock
- `GET /api/inventory/items/{sku}` returns inventory for SKU across locations
- `GET /api/inventory/items/{sku}/locations/{locationId}` returns specific inventory
- `GET /api/inventory/availability/{sku}` returns availability details
- `POST /api/inventory/availability/batch` returns availability for multiple SKUs

**Movement Queries:**
- `GET /api/inventory/movements` returns movements (paginated)
  - Query params: sku, locationId, type, startDate, endDate
- `GET /api/inventory/items/{sku}/movements` returns movements for SKU

**Reservation Queries:**
- `GET /api/inventory/reservations` returns active reservations
- `GET /api/inventory/reservations/{id}` returns reservation details
- `GET /api/inventory/reservations/by-reference/{type}/{id}` returns by reference

**Count Queries:**
- `GET /api/inventory/counts` returns count sessions
- `GET /api/inventory/counts/{id}` returns count details
- `GET /api/inventory/counts/{id}/variances` returns variance report

**Alert Queries:**
- `GET /api/inventory/alerts` returns active alerts
- `GET /api/inventory/alerts/low-stock` returns low stock items
- `GET /api/inventory/alerts/out-of-stock` returns out of stock items

### AC16: Inventory Valuation

**Implementation Plan:** [AC16 - Inventory Valuation](../plans/inventory-integration/AC16-inventory-valuation.md)

- Track cost per unit for inventory items
- Support multiple costing methods:
  - FIFO (First In, First Out)
  - LIFO (Last In, First Out)
  - Weighted Average
  - Specific Identification
- Calculate inventory value per location
- Calculate total inventory value
- Cost tracked per movement:
  - Receive records unit cost
  - Sales use appropriate costing method
  - Adjustments can specify cost impact
- Valuation report includes:
  - Total units on hand
  - Total value at cost
  - Average cost per unit
  - Value by location
  - Value by category

### AC17: Business Rules and Validation

**Implementation Plan:** [AC17 - Business Rules and Validation](../plans/inventory-integration/AC17-business-rules-validation.md)

**Location Validation:**
- Name: required, 1-100 characters, unique
- Type: required, must be valid enum
- Only one default location allowed
- Cannot close location with inventory

**Inventory Item Validation:**
- SKU: required, must exist in product catalog
- Location: required, must be active
- Quantities: non-negative integers
- Reorder point: non-negative integer
- Safety stock: non-negative integer, <= reorder point

**Movement Validation:**
- Quantity: positive integer (direction determined by type)
- Cannot reduce below zero (except with override permission)
- Reference required for receives, sales, returns
- Reason code required for adjustments, damage, loss

**Reservation Validation:**
- Cannot reserve more than available
- Expiration required for CART type
- Maximum reservation duration: 7 days
- Cannot commit expired reservation

**Count Validation:**
- Cannot start count if one in progress for location
- Counted quantity: non-negative integer
- Variance approval threshold: configurable (default: 5%)

### AC18: Resiliency and Error Handling

**Implementation Plan:** [AC18 - Resiliency and Error Handling](../plans/inventory-integration/AC18-resiliency-error-handling.md)

- Circuit breaker protects database operations
- Retry logic handles transient failures
- Rate limiting on high-volume endpoints:
  - Availability checks: 1000/minute
  - Reservations: 100/minute per user
  - Bulk operations: 10/minute
- Optimistic locking prevents overselling:
  - Version check on every update
  - Automatic retry on version conflict (up to 3 times)
- Compensation logic for failed reservations
- All errors logged with correlation IDs
- Domain exceptions mapped to HTTP responses:
  - `InsufficientInventoryException` → 409
  - `ReservationNotFoundException` → 404
  - `ReservationExpiredException` → 410
  - `LocationNotFoundException` → 404
  - `InventoryItemNotFoundException` → 404
  - `ConcurrentModificationException` → 409
  - `LocationClosedException` → 422
  - `ValidationException` → 400

### AC19: Caching Strategy

**Implementation Plan:** [AC19 - Caching Strategy](../plans/inventory-integration/AC19-caching-strategy.md)

- Availability data cached with short TTL (30 seconds)
- Location list cached with medium TTL (5 minutes)
- Inventory item details cached with short TTL (1 minute)
- Movement history not cached (real-time requirement)
- Cache invalidation triggers:
  - Any inventory movement → invalidate SKU availability
  - Reservation create/commit/release → invalidate SKU availability
  - Location update → invalidate location cache
- Cache warming for high-traffic SKUs
- Stale-while-revalidate for availability queries
- Cache bypass option for critical operations (checkout)

### AC20: Observability

**Implementation Plan:** [AC20 - Observability](../plans/inventory-integration/AC20-observability.md)

**Tracing:**
- All command operations emit trace spans
- Reservation lifecycle traced end-to-end
- Integration events traced across systems
- Database operations traced

**Metrics:**
- `inventory.quantity.on_hand` - gauge by SKU, location
- `inventory.quantity.available` - gauge by SKU, location
- `inventory.quantity.reserved` - gauge by SKU, location
- `inventory.movement.count` - counter by type
- `inventory.reservation.active` - gauge
- `inventory.reservation.expired` - counter
- `inventory.reservation.duration` - histogram
- `inventory.availability.query.duration` - histogram
- `inventory.low_stock.count` - gauge
- `inventory.out_of_stock.count` - gauge
- `inventory.adjustment.variance` - histogram

**Logging:**
- All movements logged with full context
- Reservation state changes logged
- Alert triggers logged
- Reconciliation discrepancies logged
- All logs include correlation IDs

**Dashboards:**
- Inventory overview (total value, units, locations)
- Stock status distribution (in stock, low, out)
- Movement activity (receives, sales, adjustments)
- Reservation metrics (active, conversion rate, expiration rate)
- Alert monitoring (active alerts, trends)

### AC21: Testing

**Implementation Plan:** [AC21 - Testing](../plans/inventory-integration/AC21-testing.md)

**Unit Tests:**
- All command handlers tested with `StepVerifier`
- Quantity calculations tested exhaustively
- Reservation state machine tested
- Availability status calculation tested
- Alert trigger conditions tested

**Integration Tests:**
- Full inventory CRUD operations verified
- Reservation lifecycle verified
- Concurrent reservation handling verified
- Product catalog integration verified
- Event propagation verified

**Controller Tests:**
- All endpoints tested with `@WebFluxTest` and `WebTestClient`
- Request validation tested
- Error responses verified

**Concurrency Tests:**
- Multiple simultaneous reservations for same SKU
- Race conditions in availability check and reserve
- Optimistic locking behavior verified

**Performance Tests:**
- Availability query < 50ms (p99)
- Reservation create < 100ms (p99)
- Bulk receive 1000 items < 5 seconds

**Test Coverage:**
- Minimum 80% line coverage
- 100% coverage on quantity calculations
- Test data builders for inventory objects

## Future Acceptance Criteria (Phase 2)

### AC22: Multi-Location Transfers
- Transfer stock between locations
- Transfer request and approval workflow
- In-transit inventory tracking
- Transfer completion confirmation
- Partial transfer support

### AC23: Inventory Allocation Rules
- Define allocation priority per location
- Support allocation strategies:
  - Nearest warehouse
  - Lowest cost
  - Fastest delivery
  - Round robin
- Split order across locations when needed
- Allocation override capability

### AC24: Backorder Management
- Enable backorder per SKU
- Track backorder quantity
- Backorder queue with priority
- Automatic allocation on receive
- Customer notification on fulfillment
- Backorder cancellation

### AC25: Inventory Forecasting
- Demand forecasting based on historical sales
- Seasonal adjustment factors
- Reorder point optimization
- Safety stock recommendations
- Purchase order suggestions

## Future Acceptance Criteria (Phase 3)

### AC26: Bin/Shelf Location Tracking
- Track inventory at bin level
- Bin capacity management
- Putaway suggestions
- Pick path optimization

### AC27: Lot/Batch Tracking
- Track inventory by lot number
- Lot expiration dates
- FEFO (First Expired, First Out) picking
- Lot recall support

### AC28: Serial Number Tracking
- Track individual serialized items
- Serial number validation
- Serial history tracking
- Warranty registration integration

## Definition of Done

- [ ] All Phase 1 acceptance criteria are met
- [ ] Inventory aggregate correctly tracks all quantity types
- [ ] Reservation system prevents overselling
- [ ] Expiration service reliably releases expired reservations
- [ ] Product catalog integration keeps availability in sync
- [ ] Movement history provides complete audit trail
- [ ] Physical count process handles variances correctly
- [ ] All REST endpoints are functional and documented
- [ ] Caching improves performance without stale data issues
- [ ] Resiliency patterns protect against failures and race conditions
- [ ] Observability provides visibility into inventory operations
- [ ] All tests pass with minimum coverage threshold
- [ ] Code review completed by at least one team member
- [ ] API documentation is complete and accurate
- [ ] Performance testing validates:
  - Availability query < 50ms (p99)
  - Reservation create < 100ms (p99)
  - Reservation commit < 100ms (p99)
  - Concurrent reservation accuracy: 100%

## Technical Notes

### Database Schemas

```sql
-- Command Model Schema
CREATE SCHEMA IF NOT EXISTS command_model;

-- Inventory Location
CREATE TABLE command_model.inventory_location (
    id UUID PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,
    location_type VARCHAR(20) NOT NULL,
    address JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    priority INTEGER NOT NULL DEFAULT 100,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_location_default ON command_model.inventory_location(is_default) WHERE is_default;

-- Inventory Item
CREATE TABLE command_model.inventory_item (
    id UUID PRIMARY KEY,
    sku VARCHAR(100) NOT NULL,
    location_id UUID NOT NULL REFERENCES command_model.inventory_location(id),
    quantity_on_hand INTEGER NOT NULL DEFAULT 0,
    quantity_reserved INTEGER NOT NULL DEFAULT 0,
    quantity_on_order INTEGER NOT NULL DEFAULT 0,
    reorder_point INTEGER NOT NULL DEFAULT 0,
    reorder_quantity INTEGER NOT NULL DEFAULT 0,
    safety_stock INTEGER NOT NULL DEFAULT 0,
    unit_cost_cents INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_counted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(sku, location_id),
    CONSTRAINT chk_quantity_on_hand CHECK (quantity_on_hand >= 0),
    CONSTRAINT chk_quantity_reserved CHECK (quantity_reserved >= 0),
    CONSTRAINT chk_quantity_reserved_le_on_hand CHECK (quantity_reserved <= quantity_on_hand)
);

CREATE INDEX idx_inventory_sku ON command_model.inventory_item(sku);
CREATE INDEX idx_inventory_location ON command_model.inventory_item(location_id);
CREATE INDEX idx_inventory_status ON command_model.inventory_item(status);

-- Inventory Reservation
CREATE TABLE command_model.inventory_reservation (
    id UUID PRIMARY KEY,
    sku VARCHAR(100) NOT NULL,
    location_id UUID NOT NULL REFERENCES command_model.inventory_location(id),
    quantity INTEGER NOT NULL,
    reference_type VARCHAR(20) NOT NULL,
    reference_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    reserved_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    committed_at TIMESTAMPTZ,
    released_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX idx_reservation_sku_location ON command_model.inventory_reservation(sku, location_id);
CREATE INDEX idx_reservation_reference ON command_model.inventory_reservation(reference_type, reference_id);
CREATE INDEX idx_reservation_status ON command_model.inventory_reservation(status);
CREATE INDEX idx_reservation_expires ON command_model.inventory_reservation(expires_at) WHERE status = 'ACTIVE';

-- Inventory Count Session
CREATE TABLE command_model.inventory_count (
    id UUID PRIMARY KEY,
    location_id UUID NOT NULL REFERENCES command_model.inventory_location(id),
    count_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    started_by UUID NOT NULL,
    completed_by UUID,
    notes TEXT,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE command_model.inventory_count_item (
    id UUID PRIMARY KEY,
    count_id UUID NOT NULL REFERENCES command_model.inventory_count(id),
    sku VARCHAR(100) NOT NULL,
    system_quantity INTEGER NOT NULL,
    counted_quantity INTEGER,
    variance INTEGER,
    counted_at TIMESTAMPTZ,
    counted_by UUID,
    UNIQUE(count_id, sku)
);

-- Read Model Schema
CREATE SCHEMA IF NOT EXISTS read_model;

-- Denormalized Inventory Read Model
CREATE TABLE read_model.inventory (
    id UUID PRIMARY KEY,
    sku VARCHAR(100) NOT NULL,
    product_name VARCHAR(255),
    variant_attributes JSONB,
    location_id UUID NOT NULL,
    location_name VARCHAR(100) NOT NULL,
    quantity_on_hand INTEGER NOT NULL,
    quantity_reserved INTEGER NOT NULL,
    quantity_available INTEGER NOT NULL,
    quantity_on_order INTEGER NOT NULL,
    reorder_point INTEGER NOT NULL,
    safety_stock INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    is_low_stock BOOLEAN NOT NULL DEFAULT FALSE,
    is_out_of_stock BOOLEAN NOT NULL DEFAULT FALSE,
    unit_cost_cents INTEGER,
    total_value_cents BIGINT,
    last_movement_at TIMESTAMPTZ,
    last_counted_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(sku, location_id)
);

CREATE INDEX idx_read_inventory_sku ON read_model.inventory(sku);
CREATE INDEX idx_read_inventory_location ON read_model.inventory(location_id);
CREATE INDEX idx_read_inventory_low_stock ON read_model.inventory(is_low_stock) WHERE is_low_stock;
CREATE INDEX idx_read_inventory_out_of_stock ON read_model.inventory(is_out_of_stock) WHERE is_out_of_stock;

-- Aggregated Availability View
CREATE TABLE read_model.inventory_availability (
    sku VARCHAR(100) PRIMARY KEY,
    total_on_hand INTEGER NOT NULL,
    total_reserved INTEGER NOT NULL,
    total_available INTEGER NOT NULL,
    total_on_order INTEGER NOT NULL,
    location_count INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Movement History (Append-Only)
CREATE TABLE read_model.inventory_movement (
    id UUID PRIMARY KEY,
    sku VARCHAR(100) NOT NULL,
    location_id UUID NOT NULL,
    movement_type VARCHAR(20) NOT NULL,
    quantity INTEGER NOT NULL,
    previous_on_hand INTEGER NOT NULL,
    new_on_hand INTEGER NOT NULL,
    reference_type VARCHAR(50),
    reference_id VARCHAR(100),
    reason_code VARCHAR(50),
    notes TEXT,
    unit_cost_cents INTEGER,
    performed_by UUID,
    performed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_movement_sku ON read_model.inventory_movement(sku);
CREATE INDEX idx_movement_location ON read_model.inventory_movement(location_id);
CREATE INDEX idx_movement_type ON read_model.inventory_movement(movement_type);
CREATE INDEX idx_movement_date ON read_model.inventory_movement(performed_at);
CREATE INDEX idx_movement_reference ON read_model.inventory_movement(reference_type, reference_id);

-- Active Alerts
CREATE TABLE read_model.inventory_alert (
    id UUID PRIMARY KEY,
    sku VARCHAR(100) NOT NULL,
    location_id UUID NOT NULL,
    alert_type VARCHAR(20) NOT NULL,
    threshold INTEGER,
    current_quantity INTEGER NOT NULL,
    triggered_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE(sku, location_id, alert_type) WHERE is_active
);

CREATE INDEX idx_alert_active ON read_model.inventory_alert(is_active) WHERE is_active;
CREATE INDEX idx_alert_type ON read_model.inventory_alert(alert_type) WHERE is_active;
```

### Key Dependencies

- Spring WebFlux for reactive REST endpoints
- R2DBC for reactive database access
- Jackson for JSON serialization
- Resilience4j for circuit breaker, retry, rate limiting
- Micrometer for metrics
- OpenTelemetry for distributed tracing
- Caffeine for local caching
- Spring Scheduler for reservation expiration

### Package Structure

```
com.example.cqrsspike.inventory/
├── command/
│   ├── aggregate/
│   │   ├── InventoryLocationAggregate.kt
│   │   ├── InventoryItemAggregate.kt
│   │   └── InventoryReservationAggregate.kt
│   ├── handler/
│   │   ├── InventoryLocationCommandHandler.kt
│   │   ├── InventoryAdjustmentCommandHandler.kt
│   │   ├── InventoryReservationCommandHandler.kt
│   │   └── InventoryCountCommandHandler.kt
│   └── model/
│       ├── LocationCommands.kt
│       ├── AdjustmentCommands.kt
│       ├── ReservationCommands.kt
│       └── CountCommands.kt
├── query/
│   ├── handler/
│   │   ├── InventoryQueryHandler.kt
│   │   ├── AvailabilityQueryHandler.kt
│   │   ├── MovementQueryHandler.kt
│   │   └── AlertQueryHandler.kt
│   ├── model/
│   │   ├── InventoryReadModel.kt
│   │   ├── AvailabilityReadModel.kt
│   │   ├── MovementReadModel.kt
│   │   └── AlertReadModel.kt
│   └── projection/
│       ├── InventoryProjection.kt
│       ├── AvailabilityProjection.kt
│       ├── MovementProjection.kt
│       └── AlertProjection.kt
├── event/
│   ├── InventoryReceived.kt
│   ├── InventorySold.kt
│   ├── InventoryReservationCreated.kt
│   └── ...
├── service/
│   ├── AvailabilityService.kt
│   ├── ReservationExpirationService.kt
│   ├── AlertService.kt
│   ├── InventoryValuationService.kt
│   └── ProductCatalogEventHandler.kt
├── api/
│   ├── InventoryLocationController.kt
│   ├── InventoryAdjustmentController.kt
│   ├── InventoryReservationController.kt
│   ├── InventoryQueryController.kt
│   ├── InventoryCountController.kt
│   └── dto/
│       ├── ReceiveInventoryRequest.kt
│       ├── CreateReservationRequest.kt
│       ├── AvailabilityResponse.kt
│       ├── MovementResponse.kt
│       └── ...
└── infrastructure/
    ├── InventoryLocationRepository.kt
    ├── InventoryItemRepository.kt
    ├── InventoryReservationRepository.kt
    ├── InventoryMovementRepository.kt
    └── InventoryCacheService.kt
```

### Reservation Flow

```kotlin
// Reserve inventory for order
fun reserveInventory(command: CreateReservationCommand): Mono<Reservation> {
    return inventoryRepository
        .findBySkuAndLocation(command.sku, command.locationId)
        .flatMap { item ->
            // Optimistic lock check
            if (item.version != command.expectedVersion) {
                return@flatMap Mono.error(ConcurrentModificationException())
            }

            // Availability check
            if (item.quantityAvailable < command.quantity) {
                return@flatMap Mono.error(InsufficientInventoryException(
                    requested = command.quantity,
                    available = item.quantityAvailable
                ))
            }

            // Update inventory
            val updatedItem = item.copy(
                quantityReserved = item.quantityReserved + command.quantity,
                version = item.version + 1
            )

            // Create reservation
            val reservation = Reservation(
                id = UUID.randomUUID(),
                sku = command.sku,
                locationId = command.locationId,
                quantity = command.quantity,
                referenceType = command.referenceType,
                referenceId = command.referenceId,
                status = ReservationStatus.ACTIVE,
                reservedAt = Instant.now(),
                expiresAt = command.expiresAt
            )

            // Save both atomically
            inventoryRepository.save(updatedItem)
                .then(reservationRepository.save(reservation))
                .doOnSuccess {
                    eventPublisher.publish(InventoryReservationCreated(
                        reservationId = reservation.id,
                        sku = command.sku,
                        quantity = command.quantity
                    ))
                }
        }
        .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
            .filter { it is ConcurrentModificationException })
}

// Commit reservation (convert to sale)
fun commitReservation(reservationId: UUID): Mono<Void> {
    return reservationRepository
        .findById(reservationId)
        .filter { it.status == ReservationStatus.ACTIVE }
        .switchIfEmpty(Mono.error(ReservationNotFoundException(reservationId)))
        .flatMap { reservation ->
            if (reservation.expiresAt?.isBefore(Instant.now()) == true) {
                return@flatMap Mono.error(ReservationExpiredException(reservationId))
            }

            inventoryRepository
                .findBySkuAndLocation(reservation.sku, reservation.locationId)
                .flatMap { item ->
                    val updatedItem = item.copy(
                        quantityOnHand = item.quantityOnHand - reservation.quantity,
                        quantityReserved = item.quantityReserved - reservation.quantity,
                        version = item.version + 1
                    )

                    val committedReservation = reservation.copy(
                        status = ReservationStatus.COMMITTED,
                        committedAt = Instant.now()
                    )

                    inventoryRepository.save(updatedItem)
                        .then(reservationRepository.save(committedReservation))
                        .doOnSuccess {
                            eventPublisher.publish(InventoryReservationCommitted(reservationId))
                            eventPublisher.publish(InventorySold(
                                sku = reservation.sku,
                                quantity = reservation.quantity,
                                orderId = reservation.referenceId
                            ))
                        }
                }
        }
        .then()
}

// Expiration service
@Scheduled(fixedDelay = 60000)
fun processExpiredReservations() {
    reservationRepository
        .findExpiredReservations(Instant.now())
        .flatMap { reservation ->
            releaseReservation(reservation.id)
                .doOnSuccess {
                    eventPublisher.publish(InventoryReservationExpired(reservation.id))
                }
                .onErrorResume { error ->
                    logger.error("Failed to expire reservation ${reservation.id}", error)
                    Mono.empty()
                }
        }
        .subscribe()
}
```
