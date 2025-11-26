# Feature: Product Variants (CQRS Architecture)

## Overview

As a business, we need to support products with multiple variations based on attributes like size, color, material, or any combination thereof. Each variant represents a distinct purchasable item with its own SKU, price, and inventory while sharing common product attributes (name, description, category) with its parent product. The variant system must handle complex attribute combinations (e.g., a T-shirt in 5 sizes × 8 colors = 40 variants) efficiently, including bulk operations for managing large variant matrices.

## Architecture Principles

### CQRS Separation
- **Command Side**: Manages variant definitions, attribute options, and variant instances through domain aggregates
- **Query Side**: Provides fast variant lookup, attribute filtering, and availability checking through denormalized read models
- **Event Store**: Captures all variant changes for audit trails, inventory history, and variant lifecycle analysis

### Variant Design Patterns
- **Parent-Variant Hierarchy**: Products own variants; variants cannot exist without a parent product
- **Attribute-Based Generation**: Variants are generated from combinations of attribute options
- **SKU Strategy**: Variants have unique SKUs, optionally derived from parent SKU + attribute codes
- **Price Inheritance**: Variants can inherit parent price or define their own (with optional modifiers)

### Bulk Operation Principles
- Operations are atomic at the batch level (all succeed or all fail)
- Large batches are processed asynchronously with progress tracking
- Idempotency keys prevent duplicate processing
- Rate limiting protects system stability

## Scope

### Phase 1: Core Variant Management
- Variant attribute definitions (size, color, etc.)
- Variant generation from attribute combinations
- Individual variant management (CRUD)
- Bulk variant operations (create, update, delete, price)
- Variant-level pricing

### Phase 2: Enhanced Features (Future)
- Variant images and media
- Variant-specific descriptions
- Inventory integration
- Variant availability rules

### Phase 3: Advanced Variant Management (Future)
- Variant bundles and kits
- Made-to-order variants
- Variant recommendation engine
- Cross-sell variant suggestions

## Domain Model

### Variant Attribute Definition
- Attribute ID (UUID)
- Name (e.g., "Size", "Color", "Material")
- Display Name (localized)
- Attribute Type (TEXT, COLOR_SWATCH, IMAGE_SWATCH)
- Display Order
- Status (ACTIVE, ARCHIVED)

### Variant Attribute Option
- Option ID (UUID)
- Attribute ID (UUID)
- Value (e.g., "XL", "Red", "Cotton")
- Display Value (localized)
- Code (short code for SKU generation, e.g., "XL", "RD", "COT")
- Display Order
- Metadata (hex color, image URL, etc.)
- Status (ACTIVE, ARCHIVED)

### Product Variant Configuration
- Product ID (UUID)
- Enabled Attributes (list of attribute IDs)
- SKU Generation Strategy (MANUAL, AUTO_SUFFIX, AUTO_FULL)
- Price Strategy (INHERIT, OVERRIDE, MODIFIER)
- Default Variant ID (optional)

### Product Variant
- Variant ID (UUID)
- Product ID (UUID, parent reference)
- SKU (unique)
- Attribute Values (map of attribute ID → option ID)
- Price Cents (variant-specific or null for inheritance)
- Price Modifier Cents (additive adjustment)
- Price Modifier Percent (multiplicative adjustment)
- Status (DRAFT, ACTIVE, OUT_OF_STOCK, DISCONTINUED)
- Display Order
- Is Default (boolean)
- Created/Updated timestamps
- Version (for optimistic concurrency)

### Domain Events
- `VariantAttributeCreated`
- `VariantAttributeUpdated`
- `VariantAttributeOptionAdded`
- `VariantAttributeOptionUpdated`
- `VariantAttributeOptionRemoved`
- `ProductVariantConfigured`
- `ProductVariantCreated`
- `ProductVariantUpdated`
- `ProductVariantPriceChanged`
- `ProductVariantActivated`
- `ProductVariantDiscontinued`
- `ProductVariantDeleted`
- `ProductVariantsBulkCreated`
- `ProductVariantsBulkUpdated`
- `ProductVariantsBulkDeleted`
- `ProductVariantsBulkPriceChanged`
- `VariantMatrixGenerated`
- `DefaultVariantChanged`

## Acceptance Criteria

### AC1: Variant Attribute Definition Command Model

**Implementation Plan:** [AC1 - Variant Attribute Definition Command Model](../plans/product-variants/AC1-variant-attribute-definition-command-model.md)

- A `VariantAttribute` aggregate exists in the `command_model` schema
- The aggregate supports attribute types: TEXT, COLOR_SWATCH, IMAGE_SWATCH
- The aggregate enforces business invariants:
  - Name is required and unique
  - Display order must be positive
  - At least one option must exist before activation
- The aggregate manages a collection of attribute options
- Options can be added, updated, reordered, and removed
- Option removal is blocked if used by active variants
- The aggregate generates domain events for all state changes
- All operations return `Mono<T>` for reactive compatibility

### AC2: Variant Attribute Option Management

**Implementation Plan:** [AC2 - Variant Attribute Option Management](../plans/product-variants/AC2-variant-attribute-option-management.md)

- Options belong to a single attribute (one-to-many)
- Option values are unique within an attribute
- Option codes are unique within an attribute and URL-safe
- Options have metadata appropriate to type:
  - TEXT: no additional metadata
  - COLOR_SWATCH: hex color code required
  - IMAGE_SWATCH: image URL required
- Options can be reordered within an attribute
- Archived options are excluded from new variant creation
- Archived options remain valid for existing variants

### AC3: Product Variant Configuration

**Implementation Plan:** [AC3 - Product Variant Configuration](../plans/product-variants/AC3-product-variant-configuration.md)

- Products can be configured for variant support
- Configuration specifies which attributes apply to the product
- Configuration specifies SKU generation strategy:
  - MANUAL: SKUs entered manually per variant
  - AUTO_SUFFIX: Parent SKU + option codes (e.g., "SHIRT-001-RD-XL")
  - AUTO_FULL: Generated from product code + all option codes
- Configuration specifies price strategy:
  - INHERIT: Variants use parent product price
  - OVERRIDE: Variants have independent prices
  - MODIFIER: Variants apply adjustment to parent price
- Configuration can be updated (attributes added/removed)
- Removing an attribute archives variants using that attribute
- `ProductVariantConfigured` event is emitted on configuration

### AC4: Product Variant Command Model

**Implementation Plan:** [AC4 - Product Variant Command Model](../plans/product-variants/AC4-product-variant-command-model.md)

- A `ProductVariant` aggregate exists in the `command_model` schema
- Variants belong to a single product (one-to-many)
- The aggregate enforces business invariants:
  - SKU is required and globally unique
  - Attribute values must match product's configured attributes
  - Each attribute must have exactly one option selected
  - Attribute combination must be unique within product
  - Price (if specified) must be positive
- The aggregate tracks status transitions:
  - DRAFT → ACTIVE (activation)
  - ACTIVE → OUT_OF_STOCK (inventory event)
  - ACTIVE → DISCONTINUED (manual)
  - OUT_OF_STOCK → ACTIVE (inventory event)
  - DISCONTINUED → (terminal state)
- Optimistic concurrency using version numbers
- All operations return `Mono<T>` for reactive compatibility

### AC5: Variant Command Handlers

**Implementation Plan:** [AC5 - Variant Command Handlers](../plans/product-variants/AC5-variant-command-handlers.md)

**Attribute Commands:**
- `CreateVariantAttributeCommand` creates a new attribute definition
- `UpdateVariantAttributeCommand` updates attribute details
- `AddAttributeOptionCommand` adds an option to an attribute
- `UpdateAttributeOptionCommand` updates an option
- `RemoveAttributeOptionCommand` archives an option
- `ReorderAttributeOptionsCommand` changes option display order

**Variant Commands:**
- `CreateProductVariantCommand` creates a single variant
  - Validates attribute combination uniqueness
  - Generates SKU if auto-generation configured
  - Emits `ProductVariantCreated` event
- `UpdateProductVariantCommand` updates variant details
- `ChangeVariantPriceCommand` updates variant price/modifiers
- `ActivateProductVariantCommand` transitions to ACTIVE
- `DiscontinueProductVariantCommand` transitions to DISCONTINUED
- `DeleteProductVariantCommand` removes variant (soft delete)
- `SetDefaultVariantCommand` sets the default variant for a product

### AC6: Variant Matrix Generation

**Implementation Plan:** [AC6 - Variant Matrix Generation](../plans/product-variants/AC6-variant-matrix-generation.md)

- Service generates all possible variant combinations from selected attributes
- Generation respects maximum variant limit (configurable, default: 500)
- Generation preview shows combinations without creating variants
- Generation can be filtered to specific option subsets
- Generation calculates SKUs based on configuration
- Generation sets initial prices based on strategy
- Generated variants start in DRAFT status
- `VariantMatrixGenerated` event includes count and attribute summary
- Service returns `Flux<ProductVariant>` for streaming large results

### AC7: Bulk Variant Creation

**Implementation Plan:** [AC7 - Bulk Variant Creation](../plans/product-variants/AC7-bulk-variant-creation.md)

- Endpoint accepts batch of variant definitions (max 500 per request)
- Request format:
  ```json
  {
    "productId": "uuid",
    "variants": [
      {
        "attributes": {"size": "XL", "color": "RED"},
        "sku": "PROD-XL-RD",
        "priceCents": 2999,
        "status": "DRAFT"
      }
    ],
    "options": {
      "skipDuplicates": true,
      "generateSkus": false,
      "idempotencyKey": "uuid"
    }
  }
  ```
- Validation runs on entire batch before any creation
- Duplicate SKUs within batch are detected and rejected
- Duplicate attribute combinations within batch are detected
- `skipDuplicates` option skips existing combinations instead of failing
- Operation is atomic: all variants created or none
- Response includes created count, skipped count, and variant IDs
- `ProductVariantsBulkCreated` event emitted with batch summary
- Idempotency key prevents duplicate processing on retry

### AC8: Bulk Variant Update

**Implementation Plan:** [AC8 - Bulk Variant Update](../plans/product-variants/AC8-bulk-variant-update.md)

- Endpoint accepts batch of variant updates (max 500 per request)
- Request format:
  ```json
  {
    "productId": "uuid",
    "updates": [
      {
        "variantId": "uuid",
        "priceCents": 3499,
        "status": "ACTIVE"
      }
    ],
    "options": {
      "skipMissing": false,
      "idempotencyKey": "uuid"
    }
  }
  ```
- Updates support partial fields (only specified fields change)
- Version conflicts are detected and reported per variant
- `skipMissing` option skips non-existent variants instead of failing
- Operation is atomic: all updates applied or none
- Response includes updated count, skipped count, and conflicts
- `ProductVariantsBulkUpdated` event emitted with batch summary

### AC9: Bulk Variant Price Update

**Implementation Plan:** [AC9 - Bulk Variant Price Update](../plans/product-variants/AC9-bulk-variant-price-update.md)

- Endpoint accepts batch price changes (max 1000 per request)
- Supports multiple update strategies:
  - SET: Set absolute price
  - ADJUST_FIXED: Add/subtract fixed amount
  - ADJUST_PERCENT: Apply percentage change
  - INHERIT: Reset to parent price inheritance
- Request format:
  ```json
  {
    "productId": "uuid",
    "priceUpdates": [
      {"variantId": "uuid", "strategy": "SET", "value": 2999},
      {"variantId": "uuid", "strategy": "ADJUST_PERCENT", "value": 10}
    ],
    "options": {
      "idempotencyKey": "uuid"
    }
  }
  ```
- Bulk update by filter (all variants matching criteria):
  ```json
  {
    "productId": "uuid",
    "filter": {
      "attributes": {"color": "RED"},
      "status": ["ACTIVE"]
    },
    "priceChange": {
      "strategy": "ADJUST_PERCENT",
      "value": -15
    }
  }
  ```
- Negative resulting prices are rejected
- Response includes price change summary per variant
- `ProductVariantsBulkPriceChanged` event emitted

### AC10: Bulk Variant Status Update

**Implementation Plan:** [AC10 - Bulk Variant Status Update](../plans/product-variants/AC10-bulk-variant-status-update.md)

- Endpoint accepts batch status transitions (max 500 per request)
- Request format:
  ```json
  {
    "productId": "uuid",
    "variantIds": ["uuid1", "uuid2"],
    "targetStatus": "ACTIVE",
    "options": {
      "skipInvalidTransitions": true,
      "idempotencyKey": "uuid"
    }
  }
  ```
- Bulk status by filter:
  ```json
  {
    "productId": "uuid",
    "filter": {
      "currentStatus": ["DRAFT"],
      "attributes": {"size": "XL"}
    },
    "targetStatus": "ACTIVE"
  }
  ```
- Invalid state transitions are validated before execution
- `skipInvalidTransitions` skips invalid instead of failing batch
- Response includes success count and invalid transition details

### AC11: Bulk Variant Deletion

**Implementation Plan:** [AC11 - Bulk Variant Deletion](../plans/product-variants/AC11-bulk-variant-deletion.md)

- Endpoint accepts batch of variant IDs for deletion (max 500)
- Request format:
  ```json
  {
    "productId": "uuid",
    "variantIds": ["uuid1", "uuid2"],
    "options": {
      "hardDelete": false,
      "skipMissing": true,
      "idempotencyKey": "uuid"
    }
  }
  ```
- Bulk delete by filter:
  ```json
  {
    "productId": "uuid",
    "filter": {
      "status": ["DISCONTINUED"],
      "createdBefore": "2024-01-01T00:00:00Z"
    },
    "options": {
      "hardDelete": false
    }
  }
  ```
- Soft delete (default): marks as deleted, excludes from queries
- Hard delete: permanently removes (requires confirmation)
- Cannot delete default variant (must change default first)
- Response includes deleted count and skipped IDs
- `ProductVariantsBulkDeleted` event emitted

### AC12: Async Bulk Operations

**Implementation Plan:** [AC12 - Async Bulk Operations](../plans/product-variants/AC12-async-bulk-operations.md)

- Operations exceeding threshold (configurable, default: 100) run asynchronously
- Async request returns immediately with operation ID
- Response format:
  ```json
  {
    "operationId": "uuid",
    "status": "PENDING",
    "estimatedCompletionSeconds": 30,
    "progressUrl": "/api/variants/operations/{operationId}"
  }
  ```
- Progress endpoint returns current status:
  ```json
  {
    "operationId": "uuid",
    "status": "IN_PROGRESS",
    "totalItems": 500,
    "processedItems": 250,
    "successCount": 248,
    "failureCount": 2,
    "failures": [
      {"variantId": "uuid", "error": "Duplicate SKU"}
    ],
    "startedAt": "timestamp",
    "estimatedCompletionAt": "timestamp"
  }
  ```
- Operations can be cancelled if not yet completed
- Completed operations retain results for 24 hours
- Webhook notification on completion (if configured)

### AC13: Variant Price Calculation

**Implementation Plan:** [AC13 - Variant Price Calculation](../plans/product-variants/AC13-variant-price-calculation.md)

- Service calculates effective variant price based on strategy
- Price strategies:
  - INHERIT: Use parent product base price
  - OVERRIDE: Use variant's priceCents directly
  - MODIFIER: Apply variant modifier to parent price
- Modifier calculation:
  - Fixed modifier: basePrice + modifierCents
  - Percent modifier: basePrice × (1 + modifierPercent/100)
  - Combined: (basePrice + fixedModifier) × (1 + percentModifier/100)
- Integration with pricing rules/promotions (from pricing feature)
- Effective price never goes below zero (floor at 0)
- Service returns `Mono<VariantPriceCalculation>` including:
  - Base price (from product)
  - Modifier details
  - Effective price (before promotions)
  - Final price (after promotions)
  - Applied promotions list

### AC14: Variant Read Model

**Implementation Plan:** [AC14 - Variant Read Model](../plans/product-variants/AC14-variant-read-model.md)

- Denormalized read model in `read_model` schema
- Read model includes:
  - Variant ID, Product ID, SKU
  - Attribute values (denormalized names and display values)
  - Effective price (pre-calculated)
  - Status
  - Is default flag
  - Parent product name (denormalized)
  - Inventory status (when integrated)
  - Display order
- Read model supports efficient filtering by:
  - Product ID
  - Attribute values (any combination)
  - Price range
  - Status
  - Availability
- Read model is updated from domain events
- Updates are idempotent for event replay

### AC15: Variant Query Service

**Implementation Plan:** [AC15 - Variant Query Service](../plans/product-variants/AC15-variant-query-service.md)

- Query returns single variant by ID
- Query returns single variant by SKU
- Query returns all variants for a product
- Query returns variants matching attribute filters
- Query returns available variants (in stock, active)
- Query returns variant matrix (all combinations with availability)
- Query supports pagination for large variant sets
- Query supports sorting by price, display order, name
- All queries return `Mono<T>` or `Flux<T>`
- Queries include attribute metadata for display

### AC16: Variant Selection Service

**Implementation Plan:** [AC16 - Variant Selection Service](../plans/product-variants/AC16-variant-selection-service.md)

- Service determines available options based on current selection
- Given partial selection, returns valid remaining options
- Example: If "Size: XL" selected, returns colors available in XL
- Handles out-of-stock filtering (configurable)
- Returns selection state:
  ```json
  {
    "productId": "uuid",
    "currentSelection": {"size": "XL"},
    "availableOptions": {
      "color": [
        {"id": "uuid", "value": "Red", "available": true},
        {"id": "uuid", "value": "Blue", "available": false, "reason": "OUT_OF_STOCK"}
      ]
    },
    "selectedVariant": null,
    "isComplete": false
  }
  ```
- When selection is complete, returns matched variant
- Service returns `Mono<VariantSelectionState>`

### AC17: Variant REST API (Commands)

**Implementation Plan:** [AC17 - Variant REST API Commands](../plans/product-variants/AC17-variant-rest-api-commands.md)

**Attribute Management:**
- `POST /api/variant-attributes` creates attribute definition
- `PUT /api/variant-attributes/{id}` updates attribute
- `POST /api/variant-attributes/{id}/options` adds option
- `PUT /api/variant-attributes/{id}/options/{optionId}` updates option
- `DELETE /api/variant-attributes/{id}/options/{optionId}` archives option
- `PUT /api/variant-attributes/{id}/options/reorder` reorders options

**Product Variant Configuration:**
- `POST /api/products/{productId}/variant-config` configures variants
- `PUT /api/products/{productId}/variant-config` updates configuration
- `DELETE /api/products/{productId}/variant-config` removes variant support

**Individual Variant Operations:**
- `POST /api/products/{productId}/variants` creates single variant
- `PUT /api/products/{productId}/variants/{id}` updates variant
- `PATCH /api/products/{productId}/variants/{id}/price` updates price
- `POST /api/products/{productId}/variants/{id}/activate` activates
- `POST /api/products/{productId}/variants/{id}/discontinue` discontinues
- `DELETE /api/products/{productId}/variants/{id}` deletes variant
- `POST /api/products/{productId}/variants/{id}/set-default` sets as default

**Bulk Operations:**
- `POST /api/products/{productId}/variants/bulk` bulk create
- `PUT /api/products/{productId}/variants/bulk` bulk update
- `PATCH /api/products/{productId}/variants/bulk/price` bulk price update
- `PATCH /api/products/{productId}/variants/bulk/status` bulk status update
- `DELETE /api/products/{productId}/variants/bulk` bulk delete
- `POST /api/products/{productId}/variants/generate` generate matrix

**Async Operations:**
- `GET /api/variants/operations/{operationId}` get operation status
- `DELETE /api/variants/operations/{operationId}` cancel operation

### AC18: Variant REST API (Queries)

**Implementation Plan:** [AC18 - Variant REST API Queries](../plans/product-variants/AC18-variant-rest-api-queries.md)

**Attribute Queries:**
- `GET /api/variant-attributes` returns all attributes
- `GET /api/variant-attributes/{id}` returns attribute with options
- `GET /api/variant-attributes/active` returns active attributes

**Variant Queries:**
- `GET /api/products/{productId}/variants` returns product variants
  - Query params: status, attributes, minPrice, maxPrice
- `GET /api/products/{productId}/variants/{id}` returns single variant
- `GET /api/variants/by-sku/{sku}` returns variant by SKU
- `GET /api/products/{productId}/variants/matrix` returns variant matrix
- `GET /api/products/{productId}/variants/default` returns default variant

**Selection Queries:**
- `POST /api/products/{productId}/variants/select` returns selection state
  - Body: current attribute selections
- `GET /api/products/{productId}/variants/available-options` returns all options with availability

**All endpoints:**
- Support pagination, sorting, filtering
- Include cache headers
- Return appropriate status codes

### AC19: Business Rules and Validation

**Implementation Plan:** [AC19 - Business Rules and Validation](../plans/product-variants/AC19-business-rules-validation.md)

**Attribute Validation:**
- Attribute name: required, 1-50 characters, unique
- Option value: required, 1-100 characters, unique within attribute
- Option code: required, 1-20 characters, alphanumeric + hyphen, unique within attribute
- Maximum options per attribute: 100 (configurable)

**Variant Validation:**
- SKU: required, 1-100 characters, globally unique
- Must specify exactly one option per configured attribute
- Attribute combination must be unique within product
- Maximum variants per product: 1000 (configurable)
- Price (if set): positive integer (cents)
- Price modifier percent: -99.99 to 999.99

**Bulk Operation Validation:**
- Maximum items per synchronous request: 500
- Maximum items per async request: 10000
- Idempotency key: UUID format, required for retryable operations
- Filter must not match more than 10000 variants

**State Transition Rules:**
- Only DRAFT variants can be deleted (hard delete)
- ACTIVE/OUT_OF_STOCK can be soft deleted
- DISCONTINUED is terminal (cannot transition out)
- Default variant cannot be deleted or discontinued

### AC20: Resiliency and Error Handling

**Implementation Plan:** [AC20 - Resiliency and Error Handling](../plans/product-variants/AC20-resiliency-error-handling.md)

- Circuit breaker protects database operations
- Retry logic handles transient failures
- Rate limiting on bulk endpoints:
  - 10 bulk requests per minute per user
  - 1000 total variants modified per minute per user
- Bulk operations use batched database writes
- Partial failure handling:
  - Atomic mode: all or nothing (default)
  - Best effort mode: continue on individual failures
- All errors logged with correlation IDs
- Domain exceptions mapped to HTTP responses:
  - `VariantNotFoundException` → 404
  - `DuplicateSkuException` → 409
  - `DuplicateAttributeCombinationException` → 409
  - `InvalidAttributeException` → 400
  - `InvalidStateTransitionException` → 409
  - `MaxVariantsExceededException` → 422
  - `BulkOperationLimitExceededException` → 422
  - `OperationNotFoundException` → 404
  - `OperationCancelledException` → 410

### AC21: Caching Strategy

**Implementation Plan:** [AC21 - Caching Strategy](../plans/product-variants/AC21-caching-strategy.md)

- Attribute definitions cached with long TTL (1 hour)
- Attribute options cached with medium TTL (15 minutes)
- Variant read models cached per product (5 minutes)
- Variant selection state cached per session (1 minute)
- Effective prices cached with short TTL (1 minute)
- Cache invalidation triggers:
  - Attribute changes: invalidate attribute cache
  - Option changes: invalidate attribute and product variant caches
  - Variant changes: invalidate product variant cache
  - Bulk operations: invalidate entire product variant cache
- Cache warming for frequently accessed products
- Stale-while-revalidate for variant queries

### AC22: Observability

**Implementation Plan:** [AC22 - Observability](../plans/product-variants/AC22-observability.md)

**Tracing:**
- All command operations emit trace spans
- Bulk operations include span per batch segment
- Async operations trace full lifecycle
- Price calculations traced

**Metrics:**
- `variant.count.per_product` - histogram
- `variant.attributes.count` - gauge
- `variant.bulk.operation.size` - histogram
- `variant.bulk.operation.duration` - histogram
- `variant.bulk.operation.success_rate` - gauge
- `variant.async.queue.size` - gauge
- `variant.async.operation.duration` - histogram
- `variant.selection.cache.hit_rate` - gauge
- `variant.price.calculation.duration` - histogram

**Logging:**
- Bulk operations log start, progress, completion
- Failed variant operations log full context
- Async operation state changes logged
- All logs include correlation IDs

**Dashboards:**
- Variant management overview
- Bulk operation monitoring
- Async operation queue health
- Cache effectiveness

### AC23: Testing

**Implementation Plan:** [AC23 - Testing](../plans/product-variants/AC23-testing.md)

**Unit Tests:**
- All command handlers tested with `StepVerifier`
- Attribute validation tested exhaustively
- SKU generation strategies tested
- Price calculation tested with all strategies
- State machine transitions tested

**Integration Tests:**
- Full variant CRUD operations verified
- Bulk operations verified (small and large batches)
- Async operation lifecycle verified
- Idempotency verified
- Concurrent modification handling verified

**Controller Tests:**
- All endpoints tested with `@WebFluxTest` and `WebTestClient`
- Bulk endpoint validation tested
- Error responses verified

**Performance Tests:**
- Bulk creation of 1000 variants < 5 seconds
- Variant matrix generation for 10×10 options < 1 second
- Selection service response < 50ms

**Test Coverage:**
- Minimum 80% line coverage
- 100% coverage on bulk operation logic
- Test data builders for variants

## Future Acceptance Criteria (Phase 2)

### AC24: Variant Images and Media
- Variants can have their own images
- Images can be shared across variants
- Swatch images linked to attribute options
- Image inheritance from parent product

### AC25: Variant-Specific Descriptions
- Variants can override parent description
- Description templates with attribute substitution
- SEO-friendly variant page content

### AC26: Inventory Integration
- Variant tracks inventory quantity
- Inventory changes update variant status
- Low stock alerts per variant
- Backorder support

### AC27: Variant Availability Rules
- Geographic availability per variant
- Channel-specific variant visibility
- Customer segment restrictions
- Pre-order and coming-soon states

## Definition of Done

- [ ] All Phase 1 acceptance criteria are met
- [ ] Attribute definitions correctly manage options
- [ ] Variant aggregate enforces all business rules
- [ ] SKU generation works for all strategies
- [ ] Price calculation handles all scenarios
- [ ] Bulk operations process efficiently within limits
- [ ] Async operations complete reliably with progress tracking
- [ ] All REST endpoints are functional and documented
- [ ] Caching improves performance for variant queries
- [ ] Resiliency patterns protect against failures
- [ ] Observability provides visibility into operations
- [ ] All tests pass with minimum coverage threshold
- [ ] Code review completed by at least one team member
- [ ] API documentation is complete and accurate
- [ ] Performance testing validates:
  - Single variant CRUD < 100ms (p99)
  - Bulk create 500 variants < 3 seconds (p99)
  - Variant matrix query < 200ms (p99)
  - Selection service < 50ms (p99)

## Technical Notes

### Database Schemas

```sql
-- Command Model Schema
CREATE SCHEMA IF NOT EXISTS command_model;

-- Variant Attribute Definition
CREATE TABLE command_model.variant_attribute (
    id UUID PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    attribute_type VARCHAR(20) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

-- Variant Attribute Option
CREATE TABLE command_model.variant_attribute_option (
    id UUID PRIMARY KEY,
    attribute_id UUID NOT NULL REFERENCES command_model.variant_attribute(id),
    value VARCHAR(100) NOT NULL,
    display_value VARCHAR(100) NOT NULL,
    code VARCHAR(20) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    metadata JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(attribute_id, value),
    UNIQUE(attribute_id, code)
);

CREATE INDEX idx_variant_option_attribute ON command_model.variant_attribute_option(attribute_id);

-- Product Variant Configuration
CREATE TABLE command_model.product_variant_config (
    product_id UUID PRIMARY KEY,
    enabled_attributes UUID[] NOT NULL,
    sku_strategy VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    price_strategy VARCHAR(20) NOT NULL DEFAULT 'INHERIT',
    default_variant_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

-- Product Variant
CREATE TABLE command_model.product_variant (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    sku VARCHAR(100) UNIQUE NOT NULL,
    attribute_values JSONB NOT NULL,
    price_cents INTEGER,
    price_modifier_cents INTEGER DEFAULT 0,
    price_modifier_percent DECIMAL(6,2) DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    display_order INTEGER NOT NULL DEFAULT 0,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_variant_product ON command_model.product_variant(product_id);
CREATE INDEX idx_variant_status ON command_model.product_variant(status);
CREATE INDEX idx_variant_attributes ON command_model.product_variant USING GIN(attribute_values);
CREATE UNIQUE INDEX idx_variant_default ON command_model.product_variant(product_id) WHERE is_default;

-- Read Model Schema
CREATE SCHEMA IF NOT EXISTS read_model;

-- Denormalized Variant Read Model
CREATE TABLE read_model.product_variant (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    sku VARCHAR(100) UNIQUE NOT NULL,
    attribute_values JSONB NOT NULL,
    attribute_display JSONB NOT NULL,
    base_price_cents INTEGER NOT NULL,
    effective_price_cents INTEGER NOT NULL,
    price_strategy VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    display_order INTEGER NOT NULL,
    is_default BOOLEAN NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_read_variant_product ON read_model.product_variant(product_id) WHERE NOT is_deleted;
CREATE INDEX idx_read_variant_status ON read_model.product_variant(status) WHERE NOT is_deleted;
CREATE INDEX idx_read_variant_price ON read_model.product_variant(effective_price_cents) WHERE NOT is_deleted;
CREATE INDEX idx_read_variant_attributes ON read_model.product_variant USING GIN(attribute_values) WHERE NOT is_deleted;

-- Async Operation Tracking
CREATE TABLE read_model.bulk_operation (
    id UUID PRIMARY KEY,
    operation_type VARCHAR(50) NOT NULL,
    product_id UUID,
    status VARCHAR(20) NOT NULL,
    total_items INTEGER NOT NULL,
    processed_items INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    failure_count INTEGER NOT NULL DEFAULT 0,
    failures JSONB,
    idempotency_key UUID UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_bulk_operation_status ON read_model.bulk_operation(status);
CREATE INDEX idx_bulk_operation_product ON read_model.bulk_operation(product_id);
CREATE INDEX idx_bulk_operation_expires ON read_model.bulk_operation(expires_at);
```

### Key Dependencies

- Spring WebFlux for reactive REST endpoints
- R2DBC for reactive database access
- Jackson for JSON serialization
- Resilience4j for circuit breaker, retry, rate limiting
- Micrometer for metrics
- OpenTelemetry for distributed tracing
- Caffeine for local caching
- Spring Scheduler for async operation processing

### Package Structure

```
com.example.cqrsspike.variant/
├── command/
│   ├── aggregate/
│   │   ├── VariantAttributeAggregate.kt
│   │   └── ProductVariantAggregate.kt
│   ├── handler/
│   │   ├── VariantAttributeCommandHandler.kt
│   │   ├── ProductVariantCommandHandler.kt
│   │   └── BulkVariantCommandHandler.kt
│   └── model/
│       ├── AttributeCommands.kt
│       ├── VariantCommands.kt
│       └── BulkCommands.kt
├── query/
│   ├── handler/
│   │   ├── VariantAttributeQueryHandler.kt
│   │   ├── ProductVariantQueryHandler.kt
│   │   └── VariantSelectionQueryHandler.kt
│   ├── model/
│   │   ├── VariantAttributeReadModel.kt
│   │   ├── ProductVariantReadModel.kt
│   │   └── VariantSelectionState.kt
│   └── projection/
│       ├── VariantAttributeProjection.kt
│       └── ProductVariantProjection.kt
├── event/
│   ├── VariantAttributeCreated.kt
│   ├── ProductVariantCreated.kt
│   ├── ProductVariantsBulkCreated.kt
│   └── ...
├── service/
│   ├── VariantMatrixGenerator.kt
│   ├── VariantPriceCalculator.kt
│   ├── VariantSelectionService.kt
│   ├── BulkOperationService.kt
│   └── AsyncOperationProcessor.kt
├── api/
│   ├── VariantAttributeController.kt
│   ├── ProductVariantController.kt
│   ├── BulkVariantController.kt
│   ├── AsyncOperationController.kt
│   └── dto/
│       ├── CreateVariantRequest.kt
│       ├── BulkCreateVariantsRequest.kt
│       ├── BulkUpdateVariantsRequest.kt
│       ├── BulkPriceUpdateRequest.kt
│       ├── VariantResponse.kt
│       ├── VariantSelectionResponse.kt
│       ├── AsyncOperationResponse.kt
│       └── ...
└── infrastructure/
    ├── VariantAttributeRepository.kt
    ├── ProductVariantRepository.kt
    ├── BulkOperationRepository.kt
    └── VariantCacheService.kt
```

### Bulk Operation Processing

```kotlin
// Bulk Creation with Transaction
fun bulkCreateVariants(
    command: BulkCreateVariantsCommand
): Mono<BulkOperationResult> {
    return validateBulkCreate(command)
        .flatMap { validatedVariants ->
            if (validatedVariants.size > SYNC_THRESHOLD) {
                // Queue for async processing
                queueAsyncOperation(command)
            } else {
                // Process synchronously
                processSync(validatedVariants, command.options)
            }
        }
}

// Async Operation Processing
@Scheduled(fixedDelay = 1000)
fun processAsyncOperations() {
    bulkOperationRepository
        .findPendingOperations(limit = 10)
        .flatMap { operation ->
            processOperation(operation)
                .onErrorResume { error ->
                    markOperationFailed(operation, error)
                }
        }
        .subscribe()
}

// Idempotency Check
fun checkIdempotency(key: UUID): Mono<BulkOperationResult?> {
    return bulkOperationRepository
        .findByIdempotencyKey(key)
        .filter { it.status == OperationStatus.COMPLETED }
        .map { it.toResult() }
}

// Variant Matrix Generation
fun generateVariantMatrix(
    productId: UUID,
    attributeOptions: Map<UUID, List<UUID>>
): Flux<ProductVariant> {
    val combinations = generateCombinations(attributeOptions)

    return Flux.fromIterable(combinations)
        .take(MAX_VARIANTS_PER_PRODUCT.toLong())
        .map { combination ->
            ProductVariant(
                id = UUID.randomUUID(),
                productId = productId,
                attributeValues = combination,
                sku = generateSku(productId, combination),
                status = VariantStatus.DRAFT
            )
        }
}

// Selection Service Logic
fun getSelectionState(
    productId: UUID,
    currentSelection: Map<UUID, UUID>
): Mono<VariantSelectionState> {
    return variantRepository
        .findByProductId(productId)
        .collectList()
        .map { variants ->
            val availableOptions = calculateAvailableOptions(
                variants,
                currentSelection
            )
            val matchedVariant = findMatchingVariant(
                variants,
                currentSelection
            )

            VariantSelectionState(
                productId = productId,
                currentSelection = currentSelection,
                availableOptions = availableOptions,
                selectedVariant = matchedVariant,
                isComplete = matchedVariant != null
            )
        }
}
```
