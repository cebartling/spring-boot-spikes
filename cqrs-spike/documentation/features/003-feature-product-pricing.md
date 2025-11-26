# Feature: Product Pricing Rules and Promotions (CQRS Architecture)

## Overview

As a business, we need a flexible pricing system that supports dynamic pricing rules, time-based promotions, customer segment pricing, and volume discounts. This system must integrate with the product catalog while maintaining separation of concerns, allowing pricing strategies to evolve independently of product management. The pricing engine will be implemented using CQRS with Event Sourcing to maintain a complete audit trail of all pricing decisions and enable temporal queries for analytics.

## Architecture Principles

### CQRS Separation
- **Command Side**: Manages pricing rules, promotions, and discount configurations through domain aggregates
- **Query Side**: Provides fast price calculation and rule lookup through optimized read models
- **Event Store**: Captures all pricing changes for audit, analytics, and historical price reconstruction

### Pricing Engine Design
- Rules are evaluated in priority order with first-match or best-price strategies
- Prices are always stored and calculated in cents (integer) to avoid floating-point errors
- The engine supports composable rules that can be combined for complex pricing scenarios
- All price calculations are deterministic and reproducible

### Integration Points
- Consumes `ProductCreated`, `ProductUpdated`, `ProductPriceChanged` events from Product Catalog
- Publishes `EffectivePriceChanged` events for downstream consumers (e.g., search indexes, carts)
- Provides synchronous price calculation API for real-time pricing needs

## Scope

### Phase 1: Core Pricing Engine
- Base price management
- Time-based promotions (sale prices)
- Percentage and fixed-amount discounts
- Price calculation service

### Phase 2: Advanced Pricing (Future)
- Customer segment pricing (wholesale, retail, VIP)
- Volume/quantity-based pricing tiers
- Bundle pricing
- Geographic pricing
- Promotional codes and coupons

### Phase 3: Analytics and Optimization (Future)
- Price elasticity tracking
- Promotion performance analytics
- A/B testing for pricing
- Dynamic pricing recommendations

## Domain Model

### Pricing Rule Aggregate
- Rule ID (UUID)
- Name and Description
- Rule Type (PERCENTAGE_DISCOUNT, FIXED_DISCOUNT, FIXED_PRICE, TIERED)
- Priority (lower number = higher priority)
- Status (DRAFT, ACTIVE, EXPIRED, DISABLED)
- Conditions (product scope, time range, minimum quantity, customer segment)
- Discount Value (percentage or cents)
- Created/Updated timestamps
- Version (for optimistic concurrency)

### Promotion Aggregate
- Promotion ID (UUID)
- Name and Description
- Start Date/Time
- End Date/Time
- Applicable Products (all, specific IDs, category-based)
- Stacking Rules (stackable, exclusive, best-price)
- Maximum Usage (unlimited, per-customer, total)
- Status (SCHEDULED, ACTIVE, PAUSED, EXPIRED)
- Created/Updated timestamps
- Version

### Domain Events
- `PricingRuleCreated`
- `PricingRuleUpdated`
- `PricingRuleActivated`
- `PricingRuleDeactivated`
- `PricingRuleExpired`
- `PromotionCreated`
- `PromotionScheduled`
- `PromotionStarted`
- `PromotionPaused`
- `PromotionResumed`
- `PromotionEnded`
- `PromotionExtended`
- `EffectivePriceCalculated`
- `PriceOverrideApplied`

## Acceptance Criteria

### AC1: Pricing Rule Command Model

**Implementation Plan:** [AC1 - Pricing Rule Command Model](../plans/product-pricing/AC1-pricing-rule-command-model.md)

- A `PricingRule` aggregate root exists in the `command_model` schema
- The aggregate supports rule types: PERCENTAGE_DISCOUNT, FIXED_DISCOUNT, FIXED_PRICE
- The aggregate enforces business invariants:
  - Percentage discounts must be between 0.01 and 100.00
  - Fixed discounts cannot exceed the base price they apply to
  - Priority must be a positive integer
  - Start date must be before end date (if both specified)
- The aggregate generates domain events for all state changes
- Optimistic concurrency is implemented using version numbers
- Rules can target: all products, specific product IDs, or products by category
- All command operations return `Mono<T>` for reactive compatibility

### AC2: Promotion Command Model

**Implementation Plan:** [AC2 - Promotion Command Model](../plans/product-pricing/AC2-promotion-command-model.md)

- A `Promotion` aggregate root exists in the `command_model` schema
- Promotions have defined start and end dates with timezone support
- Promotions can be scheduled for future activation
- Promotions can be paused and resumed while active
- Promotions can be extended (end date moved forward)
- Promotions cannot be modified once expired
- The aggregate enforces stacking rules:
  - EXCLUSIVE: Only this promotion applies
  - STACKABLE: Can combine with other stackable promotions
  - BEST_PRICE: System selects the best discount for customer
- Usage limits are enforced (total uses, per-customer uses)
- All state changes emit appropriate domain events

### AC3: Pricing Event Store

**Implementation Plan:** [AC3 - Pricing Event Store](../plans/product-pricing/AC3-pricing-event-store.md)

- Pricing domain events are persisted to the `event_store` schema
- Events include full context for price calculation replay
- Events support querying by:
  - Aggregate ID (rule or promotion)
  - Product ID (all pricing events affecting a product)
  - Time range (historical price analysis)
  - Event type
- Event schema supports versioning for backward compatibility
- Events are immutable once written
- Correlation IDs link related events across aggregates

### AC4: Pricing Rule Command Handlers

**Implementation Plan:** [AC4 - Pricing Rule Command Handlers](../plans/product-pricing/AC4-pricing-rule-command-handlers.md)

- `CreatePricingRuleCommand` creates a new rule and emits `PricingRuleCreated`
- `UpdatePricingRuleCommand` updates rule details (only in DRAFT status)
- `ActivatePricingRuleCommand` transitions rule to ACTIVE status
- `DeactivatePricingRuleCommand` transitions rule to DISABLED status
- `SetRulePriorityCommand` updates rule priority ordering
- `SetRuleConditionsCommand` updates rule applicability conditions
- Commands validate that rule changes don't create conflicts
- Commands are idempotent using command IDs
- Failed commands return structured error responses

### AC5: Promotion Command Handlers

**Implementation Plan:** [AC5 - Promotion Command Handlers](../plans/product-pricing/AC5-promotion-command-handlers.md)

- `CreatePromotionCommand` creates a new promotion
- `SchedulePromotionCommand` schedules a promotion for future activation
- `StartPromotionCommand` manually starts a promotion (if not scheduled)
- `PausePromotionCommand` temporarily pauses an active promotion
- `ResumePromotionCommand` resumes a paused promotion
- `ExtendPromotionCommand` extends the end date of an active promotion
- `EndPromotionCommand` manually ends a promotion before scheduled end
- `UpdatePromotionProductsCommand` modifies applicable products
- Commands enforce state transition rules (e.g., cannot pause an expired promotion)
- Concurrent modification conflicts are detected and reported

### AC6: Price Calculation Read Model

**Implementation Plan:** [AC6 - Price Calculation Read Model](../plans/product-pricing/AC6-price-calculation-read-model.md)

- A denormalized read model optimized for price lookups exists in `read_model` schema
- The read model pre-computes effective prices for active rules and promotions
- The read model includes:
  - Product ID
  - Base price (cents)
  - Effective price (cents)
  - Active discount percentage
  - Active promotion ID (if any)
  - Price valid from/until timestamps
  - Next scheduled price change
- Read model updates are triggered by pricing and product events
- Updates are processed within acceptable latency (< 1 second)
- Read model supports point-in-time price queries

### AC7: Active Promotions Read Model

**Implementation Plan:** [AC7 - Active Promotions Read Model](../plans/product-pricing/AC7-active-promotions-read-model.md)

- A read model tracks all currently active promotions
- The read model includes promotion details needed for display:
  - Promotion name and description
  - Discount summary (e.g., "20% off", "$10 off")
  - Applicable product count
  - Time remaining
  - Terms and conditions
- Scheduled promotions are visible with countdown to start
- Recently ended promotions are available for a configurable period
- Read model supports filtering by product applicability

### AC8: Price Calculation Service

**Implementation Plan:** [AC8 - Price Calculation Service](../plans/product-pricing/AC8-price-calculation-service.md)

- Service calculates effective price for a single product
- Service calculates effective prices for multiple products (batch)
- Calculation considers all applicable active rules and promotions
- Calculation respects rule priority ordering
- Calculation applies stacking rules correctly:
  - For EXCLUSIVE: applies only that promotion
  - For STACKABLE: combines applicable discounts
  - For BEST_PRICE: selects lowest resulting price
- Service returns breakdown of applied discounts
- Service returns `Mono<PriceCalculation>` or `Flux<PriceCalculation>`
- Calculation results are cacheable with appropriate TTL
- Service handles missing products gracefully (returns error, not exception)

### AC9: Pricing REST API (Commands)

**Implementation Plan:** [AC9 - Pricing REST API Commands](../plans/product-pricing/AC9-pricing-rest-api-commands.md)

**Pricing Rules:**
- `POST /api/pricing/rules` creates a new pricing rule
- `PUT /api/pricing/rules/{id}` updates a pricing rule
- `POST /api/pricing/rules/{id}/activate` activates a rule
- `POST /api/pricing/rules/{id}/deactivate` deactivates a rule
- `DELETE /api/pricing/rules/{id}` deletes a draft rule

**Promotions:**
- `POST /api/pricing/promotions` creates a new promotion
- `PUT /api/pricing/promotions/{id}` updates a promotion
- `POST /api/pricing/promotions/{id}/schedule` schedules a promotion
- `POST /api/pricing/promotions/{id}/start` starts a promotion
- `POST /api/pricing/promotions/{id}/pause` pauses a promotion
- `POST /api/pricing/promotions/{id}/resume` resumes a promotion
- `POST /api/pricing/promotions/{id}/extend` extends a promotion
- `POST /api/pricing/promotions/{id}/end` ends a promotion
- `DELETE /api/pricing/promotions/{id}` deletes a draft promotion

**All endpoints:**
- Use DTOs for request/response (never expose domain entities)
- Include OpenAPI/Swagger documentation
- Return appropriate HTTP status codes
- Validate input and return structured error responses

### AC10: Pricing REST API (Queries)

**Implementation Plan:** [AC10 - Pricing REST API Queries](../plans/product-pricing/AC10-pricing-rest-api-queries.md)

**Price Lookups:**
- `GET /api/pricing/products/{productId}/price` returns effective price for a product
- `POST /api/pricing/products/prices` returns effective prices for multiple products (batch)
- `GET /api/pricing/products/{productId}/price-history` returns historical prices

**Pricing Rules:**
- `GET /api/pricing/rules` returns paginated list of pricing rules
- `GET /api/pricing/rules/{id}` returns a single pricing rule
- `GET /api/pricing/rules/active` returns currently active rules
- `GET /api/pricing/products/{productId}/rules` returns rules applicable to a product

**Promotions:**
- `GET /api/pricing/promotions` returns paginated list of promotions
- `GET /api/pricing/promotions/{id}` returns a single promotion
- `GET /api/pricing/promotions/active` returns currently active promotions
- `GET /api/pricing/promotions/scheduled` returns upcoming scheduled promotions
- `GET /api/pricing/products/{productId}/promotions` returns promotions for a product

**Query Parameters:**
- Support filtering, sorting, and pagination
- Response includes pagination metadata
- Cache headers set appropriately

### AC11: Time-Based Promotion Scheduler

**Implementation Plan:** [AC11 - Time-Based Promotion Scheduler](../plans/product-pricing/AC11-time-based-promotion-scheduler.md)

- A scheduler service monitors promotion start/end times
- Promotions automatically transition to ACTIVE at scheduled start time
- Promotions automatically transition to EXPIRED at scheduled end time
- Scheduler handles timezone-aware date/time comparisons
- Scheduler is resilient to restarts (catches up on missed transitions)
- Scheduler emits `PromotionStarted` and `PromotionEnded` events
- Scheduler runs with configurable polling interval (default: 1 minute)
- Scheduler uses distributed locking to prevent duplicate processing
- Manual start/end commands override scheduled transitions

### AC12: Product Catalog Integration

**Implementation Plan:** [AC12 - Product Catalog Integration](../plans/product-pricing/AC12-product-catalog-integration.md)

- Pricing service subscribes to Product Catalog events
- `ProductCreated` triggers creation of base price entry
- `ProductPriceChanged` updates base price in pricing read model
- `ProductDiscontinued` marks product prices as inactive
- `ProductDeleted` handles cleanup of associated pricing rules
- Integration uses event-driven architecture (not synchronous calls)
- Event processing is idempotent and handles replays
- Failed event processing is logged and can be retried

### AC13: Business Rules and Validation

**Implementation Plan:** [AC13 - Business Rules and Validation](../plans/product-pricing/AC13-business-rules-validation.md)

**Pricing Rules:**
- Rule name is required (1-100 characters)
- Percentage discounts: 0.01% to 100.00%
- Fixed discounts: positive integer (cents)
- Fixed prices: positive integer (cents)
- Priority: positive integer (1-1000, lower = higher priority)
- At least one product condition must be specified
- Rules cannot overlap with conflicting priorities for same products

**Promotions:**
- Promotion name is required (1-100 characters)
- Start date is required
- End date must be after start date
- End date cannot be more than 1 year from start date
- At least one product must be applicable
- Usage limits must be positive integers if specified
- Paused promotions automatically expire at original end date

**Price Calculations:**
- Final price cannot be negative (floor at 0)
- Final price cannot exceed base price (no negative discounts)
- Discount percentages are applied to base price, not cascaded
- Cent fractions are rounded using banker's rounding

### AC14: Resiliency and Error Handling

**Implementation Plan:** [AC14 - Resiliency and Error Handling](../plans/product-pricing/AC14-resiliency-error-handling.md)

- Circuit breaker protects database operations
- Circuit breaker protects external service calls
- Retry logic handles transient failures with exponential backoff
- Rate limiting prevents abuse of command endpoints
- Rate limiting on price calculation prevents DoS
- Fallback for price calculation returns base price with warning
- All errors logged with correlation IDs
- Domain exceptions mapped to appropriate HTTP responses:
  - `RuleNotFoundException` ’ 404
  - `PromotionNotFoundException` ’ 404
  - `InvalidRuleStateException` ’ 409
  - `InvalidPromotionStateException` ’ 409
  - `RuleConflictException` ’ 409
  - `ValidationException` ’ 400
- Concurrent modification returns 409 with current version

### AC15: Observability

**Implementation Plan:** [AC15 - Observability](../plans/product-pricing/AC15-observability.md)

**Tracing:**
- All command operations emit trace spans
- All query operations emit trace spans
- Price calculations include span with rule evaluation details
- Event processing includes trace context propagation

**Metrics:**
- `pricing.rules.active.count` - gauge of active rules
- `pricing.promotions.active.count` - gauge of active promotions
- `pricing.calculation.duration` - histogram of calculation times
- `pricing.calculation.rules.evaluated` - histogram of rules per calculation
- `pricing.command.success` / `pricing.command.failure` - counters by type
- `pricing.event.processed` / `pricing.event.failed` - counters by type
- `pricing.cache.hit` / `pricing.cache.miss` - cache effectiveness

**Logging:**
- Price calculations log input, applied rules, and result
- Rule/promotion state changes log before and after states
- All logs include correlation IDs
- Sensitive data (customer IDs) is masked in logs

**Dashboards:**
- Pricing system health overview
- Active promotions and their performance
- Price calculation latency and throughput
- Event processing lag

### AC16: Caching Strategy

**Implementation Plan:** [AC16 - Caching Strategy](../plans/product-pricing/AC16-caching-strategy.md)

- Active pricing rules are cached with configurable TTL
- Active promotions are cached with time-aware TTL (expires at promotion end)
- Price calculations are cached by product ID with short TTL
- Cache is invalidated on rule/promotion changes
- Cache warming occurs on application startup
- Cache statistics are exposed as metrics
- Cache keys use consistent hashing for distribution
- Stale cache entries are served during backend failures (stale-while-revalidate)

### AC17: Testing

**Implementation Plan:** [AC17 - Testing](../plans/product-pricing/AC17-testing.md)

**Unit Tests:**
- All command handlers tested with `StepVerifier`
- All query handlers tested with `StepVerifier`
- Pricing rule aggregate business logic tested
- Promotion aggregate state machine tested
- Price calculation service tested with various rule combinations
- Stacking rules tested exhaustively

**Integration Tests:**
- Event store persistence verified
- Projection updates verified
- Scheduler behavior verified (with time manipulation)
- Product catalog integration verified
- Cache behavior verified

**Controller Tests:**
- All endpoints tested with `@WebFluxTest` and `WebTestClient`
- Request validation tested
- Error response format verified

**Test Coverage:**
- Minimum 80% line coverage
- 100% coverage on price calculation logic
- Test data builders for all domain objects
- Property-based tests for price calculation edge cases

## Future Acceptance Criteria (Phase 2)

### AC18: Customer Segment Pricing
- Customers can be assigned to pricing segments (RETAIL, WHOLESALE, VIP)
- Pricing rules can target specific customer segments
- Price calculation considers customer context
- Segment changes emit events for price recalculation

### AC19: Volume/Quantity Pricing
- Pricing rules support quantity-based tiers
- Tiers can use unit price or total price model
- Price calculation accepts quantity parameter
- Break points are clearly communicated in API response

### AC20: Bundle Pricing
- Products can be grouped into bundles
- Bundles have their own pricing rules
- Bundle discount can be percentage or fixed amount
- Individual products in bundle can have preserved or overridden prices

### AC21: Promotional Codes
- Promotions can require a code for activation
- Codes can be single-use or multi-use
- Code validation is case-insensitive
- Invalid/expired codes return clear error messages
- Code usage is tracked and enforced

### AC22: Geographic Pricing
- Prices can vary by geographic region
- Currency conversion is supported
- Tax considerations are factored in
- Region detection is configurable (IP, customer profile)

## Definition of Done

- [ ] All Phase 1 acceptance criteria are met
- [ ] Pricing rules correctly enforce business invariants
- [ ] Promotions follow defined state machine transitions
- [ ] Price calculations are accurate and deterministic
- [ ] Event store captures complete pricing history
- [ ] Read models are consistently updated from events
- [ ] All REST endpoints are functional and documented
- [ ] Time-based scheduler reliably manages promotion lifecycle
- [ ] Product catalog integration handles all relevant events
- [ ] Resiliency patterns are implemented and tested
- [ ] Caching improves performance without causing stale prices
- [ ] Observability is configured with dashboards available
- [ ] All tests pass with minimum coverage threshold
- [ ] Code review completed by at least one team member
- [ ] API documentation is complete and accurate
- [ ] Performance testing validates:
  - Price calculation < 50ms (p99)
  - Batch price calculation < 200ms for 100 products (p99)
  - Rule updates reflected in read model < 1 second

## Technical Notes

### Database Schemas

```sql
-- Event Store (extends existing schema)
-- Events: PricingRuleCreated, PromotionStarted, etc.

-- Read Model Schema
CREATE SCHEMA IF NOT EXISTS read_model;

-- Pricing Rules Read Model
CREATE TABLE read_model.pricing_rule (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    rule_type VARCHAR(30) NOT NULL,
    priority INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    discount_value INTEGER NOT NULL,
    discount_type VARCHAR(20) NOT NULL,
    conditions JSONB NOT NULL,
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL
);

CREATE INDEX idx_pricing_rule_status ON read_model.pricing_rule(status);
CREATE INDEX idx_pricing_rule_priority ON read_model.pricing_rule(priority) WHERE status = 'ACTIVE';

-- Promotions Read Model
CREATE TABLE read_model.promotion (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL,
    stacking_rule VARCHAR(20) NOT NULL,
    start_date TIMESTAMPTZ NOT NULL,
    end_date TIMESTAMPTZ NOT NULL,
    applicable_products JSONB NOT NULL,
    max_total_uses INTEGER,
    max_uses_per_customer INTEGER,
    current_usage_count INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL
);

CREATE INDEX idx_promotion_status ON read_model.promotion(status);
CREATE INDEX idx_promotion_dates ON read_model.promotion(start_date, end_date);

-- Effective Price Cache (materialized view or table)
CREATE TABLE read_model.effective_price (
    product_id UUID PRIMARY KEY,
    base_price_cents INTEGER NOT NULL,
    effective_price_cents INTEGER NOT NULL,
    discount_percentage DECIMAL(5,2),
    applied_rule_id UUID,
    applied_promotion_id UUID,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    calculated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_effective_price_valid ON read_model.effective_price(valid_until);

-- Price History (for analytics)
CREATE TABLE read_model.price_history (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    price_cents INTEGER NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_until TIMESTAMPTZ,
    change_reason VARCHAR(50) NOT NULL,
    rule_id UUID,
    promotion_id UUID,
    recorded_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_price_history_product ON read_model.price_history(product_id, effective_from);
```

### Key Dependencies

- Spring WebFlux for reactive REST endpoints
- R2DBC for reactive database access
- Jackson for JSON serialization of events and conditions
- Resilience4j for circuit breaker, retry, rate limiting
- Micrometer for metrics
- OpenTelemetry for distributed tracing
- Spring Scheduler for promotion lifecycle management
- Caffeine for local caching

### Package Structure

```
com.example.cqrsspike.pricing/
   command/
      aggregate/
         PricingRuleAggregate.kt
         PromotionAggregate.kt
      handler/
         PricingRuleCommandHandler.kt
         PromotionCommandHandler.kt
      model/
          PricingCommands.kt
          PromotionCommands.kt
   query/
      handler/
         PricingRuleQueryHandler.kt
         PromotionQueryHandler.kt
         PriceCalculationQueryHandler.kt
      model/
         PricingRuleReadModel.kt
         PromotionReadModel.kt
         EffectivePriceReadModel.kt
      projection/
          PricingRuleProjection.kt
          PromotionProjection.kt
          EffectivePriceProjection.kt
   event/
      PricingRuleCreated.kt
      PromotionStarted.kt
      ...
   service/
      PriceCalculationService.kt
      PromotionSchedulerService.kt
      ProductCatalogEventHandler.kt
   api/
      PricingRuleCommandController.kt
      PricingRuleQueryController.kt
      PromotionCommandController.kt
      PromotionQueryController.kt
      PriceCalculationController.kt
      dto/
          CreatePricingRuleRequest.kt
          CreatePromotionRequest.kt
          PriceCalculationResponse.kt
          ...
   infrastructure/
      PricingRuleRepository.kt
      PromotionRepository.kt
      EffectivePriceRepository.kt
      PricingCacheService.kt
   integration/
       ProductCatalogEventListener.kt
```

### Price Calculation Algorithm

```kotlin
fun calculateEffectivePrice(
    productId: UUID,
    basePrice: Int,
    customerSegment: CustomerSegment? = null,
    quantity: Int = 1
): PriceCalculation {
    // 1. Get all applicable active rules, sorted by priority
    val applicableRules = getApplicableRules(productId, customerSegment)

    // 2. Get all applicable active promotions
    val applicablePromotions = getApplicablePromotions(productId)

    // 3. Evaluate rules based on stacking strategy
    val ruleDiscount = evaluateRules(applicableRules, basePrice)

    // 4. Evaluate promotions based on stacking rules
    val promotionDiscount = evaluatePromotions(applicablePromotions, basePrice)

    // 5. Combine discounts (rules and promotions don't stack by default)
    val totalDiscount = maxOf(ruleDiscount, promotionDiscount)

    // 6. Calculate final price (floor at 0)
    val effectivePrice = maxOf(0, basePrice - totalDiscount)

    return PriceCalculation(
        productId = productId,
        basePriceCents = basePrice,
        effectivePriceCents = effectivePrice,
        discountCents = totalDiscount,
        discountPercentage = (totalDiscount.toDouble() / basePrice * 100).toBigDecimal(),
        appliedRules = applicableRules.map { it.id },
        appliedPromotions = applicablePromotions.map { it.id }
    )
}
```
