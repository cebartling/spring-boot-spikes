# Feature: Product Categories and Hierarchies (CQRS Architecture)

## Overview

As a business, we need a flexible category system that organizes products into logical groupings with support for multi-level hierarchies (e.g., Electronics → Computers → Laptops → Gaming Laptops). This system enables intuitive navigation, targeted marketing, category-specific pricing rules, and efficient product discovery. The category hierarchy will be implemented using CQRS with Event Sourcing to maintain a complete history of taxonomy changes and support multiple hierarchy views for different channels.

## Architecture Principles

### CQRS Separation
- **Command Side**: Manages category creation, hierarchy modifications, and product assignments through domain aggregates
- **Query Side**: Provides fast hierarchy traversal, breadcrumb generation, and product filtering through materialized views
- **Event Store**: Captures all taxonomy changes for audit trails, analytics, and hierarchy reconstruction at any point in time

### Hierarchy Design Patterns
- **Adjacency List**: Each category stores reference to its parent (simple, flexible)
- **Materialized Path**: Each category stores full path from root (fast reads, complex writes)
- **Nested Sets**: Each category has left/right bounds (very fast subtree queries, complex updates)
- **Closure Table**: Separate table stores all ancestor-descendant relationships (balanced read/write)

This implementation uses a **hybrid approach**: Adjacency List for the command model (simple updates) combined with Materialized Path and Closure Table in read models (fast queries).

### Constraints
- Maximum hierarchy depth: 10 levels (configurable)
- A product can belong to multiple categories (many-to-many)
- Categories inherit attributes from ancestors (optional override)
- Circular references are prevented at the domain level

## Scope

### Phase 1: Core Category Hierarchy
- Category aggregate with parent-child relationships
- Multi-level hierarchy support (unlimited depth with configurable max)
- Product-to-category assignments
- Hierarchy traversal queries
- Breadcrumb generation

### Phase 2: Enhanced Features (Future)
- Category attributes and inheritance
- Category-specific display templates
- Faceted navigation configuration
- SEO metadata per category
- Multi-channel category trees

### Phase 3: Advanced Taxonomy (Future)
- Multiple category taxonomies (e.g., by department, by use case)
- Category synonyms and aliases
- AI-powered category suggestions
- Category performance analytics

## Domain Model

### Category Aggregate
- Category ID (UUID)
- Name (localized)
- Slug (URL-friendly identifier)
- Description (localized)
- Parent Category ID (nullable for root categories)
- Display Order (sibling ordering)
- Status (DRAFT, ACTIVE, ARCHIVED)
- Depth Level (computed, 0 for root)
- Metadata (JSONB for extensibility)
- Created/Updated timestamps
- Version (for optimistic concurrency)

### Product-Category Assignment
- Assignment ID (UUID)
- Product ID (UUID)
- Category ID (UUID)
- Is Primary (boolean - main category for product)
- Display Order (within category)
- Assigned At timestamp

### Domain Events
- `CategoryCreated`
- `CategoryRenamed`
- `CategoryDescriptionUpdated`
- `CategoryMoved` (parent changed)
- `CategoryReordered` (display order changed)
- `CategoryActivated`
- `CategoryArchived`
- `CategoryDeleted`
- `ProductAssignedToCategory`
- `ProductUnassignedFromCategory`
- `ProductPrimaryCategoryChanged`
- `CategoryHierarchyRestructured` (bulk operation)

## Acceptance Criteria

### AC1: Category Command Model

**Implementation Plan:** [AC1 - Category Command Model](../plans/product-categories/AC1-category-command-model.md)

- A `Category` aggregate root exists in the `command_model` schema
- The aggregate stores parent category ID for hierarchy relationship
- The aggregate enforces business invariants:
  - Name is required and unique among siblings
  - Slug is required, URL-safe, and globally unique
  - Parent category must exist and be active (if specified)
  - Cannot set self as parent (direct circular reference)
  - Cannot set descendant as parent (indirect circular reference)
- The aggregate computes and stores depth level based on parent
- The aggregate generates domain events for all state changes
- Optimistic concurrency is implemented using version numbers
- All command operations return `Mono<T>` for reactive compatibility

### AC2: Hierarchy Depth Management

**Implementation Plan:** [AC2 - Hierarchy Depth Management](../plans/product-categories/AC2-hierarchy-depth-management.md)

- Maximum hierarchy depth is configurable (default: 10 levels)
- Depth is computed as parent depth + 1 (root categories have depth 0)
- Moving a category validates that new depth doesn't exceed maximum
- Moving a category with descendants validates entire subtree depth
- Attempting to exceed maximum depth returns clear error with current/max values
- Depth changes propagate to all descendants when category is moved
- Depth is stored in both command and read models for efficiency

### AC3: Circular Reference Prevention

**Implementation Plan:** [AC3 - Circular Reference Prevention](../plans/product-categories/AC3-circular-reference-prevention.md)

- Direct circular references are prevented (category cannot be its own parent)
- Indirect circular references are prevented (category cannot have descendant as parent)
- Circular reference check is performed before any hierarchy modification
- Check uses closure table or ancestor traversal for efficiency
- Circular reference attempts return clear error identifying the cycle
- Validation is performed in domain layer, not just database constraints

### AC4: Category Command Handlers

**Implementation Plan:** [AC4 - Category Command Handlers](../plans/product-categories/AC4-category-command-handlers.md)

- `CreateCategoryCommand` creates a new category (root or child)
  - Validates parent exists and is active (if specified)
  - Validates name uniqueness among siblings
  - Validates slug global uniqueness
  - Computes initial depth
  - Emits `CategoryCreated` event
- `RenameCategoryCommand` updates category name
  - Validates new name uniqueness among siblings
  - Emits `CategoryRenamed` event
- `UpdateCategoryDescriptionCommand` updates description
  - Emits `CategoryDescriptionUpdated` event
- `MoveCategoryCommand` changes parent category
  - Validates no circular reference
  - Validates depth constraints for subtree
  - Updates depth for category and all descendants
  - Emits `CategoryMoved` event
- `ReorderCategoryCommand` changes display order among siblings
  - Emits `CategoryReordered` event
- `ActivateCategoryCommand` transitions to ACTIVE status
  - Validates parent is active (if exists)
  - Emits `CategoryActivated` event
- `ArchiveCategoryCommand` transitions to ARCHIVED status
  - Optionally archives all descendants
  - Emits `CategoryArchived` event
- `DeleteCategoryCommand` removes category
  - Validates no products assigned (or reassigns to parent)
  - Validates no child categories (or cascade delete)
  - Emits `CategoryDeleted` event

### AC5: Category Hierarchy Event Store

**Implementation Plan:** [AC5 - Category Hierarchy Event Store](../plans/product-categories/AC5-category-hierarchy-event-store.md)

- Category domain events are persisted to the `event_store` schema
- Events capture full context for hierarchy reconstruction
- `CategoryMoved` events include:
  - Previous parent ID
  - New parent ID
  - Previous depth
  - New depth
  - Affected descendant count
- Events support querying by:
  - Category ID
  - Parent category ID
  - Time range (hierarchy at point in time)
  - Event type
- Events enable reconstruction of hierarchy at any historical moment
- Correlation IDs link related events (e.g., cascade operations)

### AC6: Materialized Path Read Model

**Implementation Plan:** [AC6 - Materialized Path Read Model](../plans/product-categories/AC6-materialized-path-read-model.md)

- A read model stores materialized path for each category
- Path format: `/root-id/child-id/grandchild-id/` (using UUIDs or slugs)
- Path enables efficient ancestor queries using LIKE prefix matching
- Path enables efficient descendant queries using LIKE prefix matching
- Path is updated when category is moved (and propagates to descendants)
- Read model includes:
  - Category ID
  - Full materialized path
  - Path depth (count of segments)
  - Path as slug chain (for URLs)
  - Path as name chain (for breadcrumbs)
- Path updates are idempotent and handle event replays

### AC7: Closure Table Read Model

**Implementation Plan:** [AC7 - Closure Table Read Model](../plans/product-categories/AC7-closure-table-read-model.md)

- A closure table stores all ancestor-descendant relationships
- Each row contains: ancestor ID, descendant ID, depth distance
- Self-referential rows exist (ancestor = descendant, depth = 0)
- Enables O(1) subtree queries without recursion
- Enables efficient ancestor chain retrieval
- Enables efficient "all descendants at depth N" queries
- Table is updated when categories are created, moved, or deleted
- Updates handle full subtree recalculation efficiently
- Read model includes indexes for both ancestor and descendant queries

### AC8: Hierarchy Traversal Queries

**Implementation Plan:** [AC8 - Hierarchy Traversal Queries](../plans/product-categories/AC8-hierarchy-traversal-queries.md)

- Query returns all ancestors of a category (root to parent chain)
- Query returns all descendants of a category (full subtree)
- Query returns direct children of a category
- Query returns siblings of a category
- Query returns path from one category to another (if related)
- Query returns categories at a specific depth level
- Query returns root categories only
- Query returns leaf categories only (no children)
- All queries return `Flux<Category>` for reactive streaming
- Queries support optional depth limiting for large hierarchies

### AC9: Breadcrumb Generation

**Implementation Plan:** [AC9 - Breadcrumb Generation](../plans/product-categories/AC9-breadcrumb-generation.md)

- Service generates breadcrumb trail from root to specified category
- Breadcrumb includes: category ID, name, slug, depth, URL path
- Breadcrumb is ordered from root (index 0) to current category
- Breadcrumb generation uses materialized path for efficiency (single query)
- Breadcrumb response includes:
  ```json
  {
    "categoryId": "uuid",
    "breadcrumbs": [
      {"id": "uuid", "name": "Electronics", "slug": "electronics", "depth": 0, "url": "/electronics"},
      {"id": "uuid", "name": "Computers", "slug": "computers", "depth": 1, "url": "/electronics/computers"},
      {"id": "uuid", "name": "Laptops", "slug": "laptops", "depth": 2, "url": "/electronics/computers/laptops"}
    ]
  }
  ```
- Breadcrumb is cached with category-based invalidation
- Service returns `Mono<BreadcrumbResponse>` for reactive compatibility

### AC10: Product-Category Assignment

**Implementation Plan:** [AC10 - Product-Category Assignment](../plans/product-categories/AC10-product-category-assignment.md)

- Products can be assigned to multiple categories
- One category must be designated as primary for each product
- Primary category is used for canonical URL and main navigation
- Assignment tracks display order within category
- `AssignProductToCategoryCommand` adds product to category
  - Validates product exists
  - Validates category exists and is active
  - Sets as primary if first assignment
  - Emits `ProductAssignedToCategory` event
- `UnassignProductFromCategoryCommand` removes product from category
  - Cannot remove primary if other assignments exist
  - Emits `ProductUnassignedFromCategory` event
- `SetPrimaryCategoryCommand` changes primary category
  - Validates product is assigned to target category
  - Emits `ProductPrimaryCategoryChanged` event
- `ReorderProductInCategoryCommand` changes display order
- Bulk assignment operations are supported for efficiency

### AC11: Category-Based Product Queries

**Implementation Plan:** [AC11 - Category-Based Product Queries](../plans/product-categories/AC11-category-based-product-queries.md)

- Query returns products in a specific category (direct assignment)
- Query returns products in a category and all descendants (recursive)
- Query supports pagination (cursor and offset-based)
- Query supports sorting by: name, price, date added, display order
- Query supports filtering by: status, price range, attributes
- Query returns product count per category (for navigation display)
- Query returns product count including descendants (rollup count)
- Recursive queries use closure table for efficiency
- All queries return `Flux<Product>` with pagination metadata

### AC12: Category Navigation Tree

**Implementation Plan:** [AC12 - Category Navigation Tree](../plans/product-categories/AC12-category-navigation-tree.md)

- Query returns full category tree structure
- Query returns partial tree (specific depth limit)
- Query returns tree branch (specific category and descendants)
- Tree response includes nested structure:
  ```json
  {
    "id": "uuid",
    "name": "Electronics",
    "slug": "electronics",
    "productCount": 150,
    "children": [
      {
        "id": "uuid",
        "name": "Computers",
        "slug": "computers",
        "productCount": 75,
        "children": [...]
      }
    ]
  }
  ```
- Tree includes product counts (direct and recursive)
- Tree respects display order for sibling ordering
- Tree excludes archived categories by default (configurable)
- Tree is cached with subtree-aware invalidation
- Service returns `Mono<CategoryTreeNode>` for root

### AC13: Category REST API (Commands)

**Implementation Plan:** [AC13 - Category REST API Commands](../plans/product-categories/AC13-category-rest-api-commands.md)

**Category Management:**
- `POST /api/categories` creates a new category
  - Body includes: name, slug, description, parentId (optional)
  - Returns 201 with created category
- `PUT /api/categories/{id}` updates category details
- `PATCH /api/categories/{id}/name` renames category
- `PATCH /api/categories/{id}/description` updates description
- `POST /api/categories/{id}/move` moves category to new parent
  - Body includes: newParentId (null for root)
  - Returns 200 with updated category and new path
- `POST /api/categories/{id}/reorder` changes display order
  - Body includes: newPosition or afterSiblingId
- `POST /api/categories/{id}/activate` activates category
- `POST /api/categories/{id}/archive` archives category
- `DELETE /api/categories/{id}` deletes category
  - Query param: `reassignTo` (category ID for products)
  - Query param: `cascade` (delete children)

**Product Assignment:**
- `POST /api/categories/{id}/products` assigns products to category
  - Body includes: productIds[], isPrimary
- `DELETE /api/categories/{id}/products/{productId}` unassigns product
- `PUT /api/categories/{id}/products/{productId}/primary` sets as primary
- `PUT /api/categories/{id}/products/reorder` reorders products

**All endpoints:**
- Use DTOs for request/response
- Include OpenAPI/Swagger documentation
- Return appropriate HTTP status codes
- Validate input and return structured error responses

### AC14: Category REST API (Queries)

**Implementation Plan:** [AC14 - Category REST API Queries](../plans/product-categories/AC14-category-rest-api-queries.md)

**Category Queries:**
- `GET /api/categories` returns paginated list of categories
  - Query params: status, depth, parentId
- `GET /api/categories/{id}` returns single category with metadata
- `GET /api/categories/by-slug/{slug}` returns category by slug
- `GET /api/categories/roots` returns root categories
- `GET /api/categories/tree` returns full category tree
  - Query param: maxDepth (limit tree depth)
- `GET /api/categories/{id}/tree` returns subtree from category
- `GET /api/categories/{id}/ancestors` returns ancestor chain
- `GET /api/categories/{id}/descendants` returns all descendants
  - Query param: maxDepth
- `GET /api/categories/{id}/children` returns direct children
- `GET /api/categories/{id}/siblings` returns sibling categories
- `GET /api/categories/{id}/breadcrumb` returns breadcrumb trail
- `GET /api/categories/{id}/path` returns URL path segments

**Product Queries:**
- `GET /api/categories/{id}/products` returns products in category
  - Query param: includeDescendants (recursive search)
  - Query params: page, size, sort, direction
- `GET /api/categories/{id}/product-count` returns product counts
  - Response: direct count and recursive count

**All endpoints:**
- Support filtering, sorting, pagination
- Include cache headers
- Support content negotiation

### AC15: Hierarchy Integrity Validation

**Implementation Plan:** [AC15 - Hierarchy Integrity Validation](../plans/product-categories/AC15-hierarchy-integrity-validation.md)

- Scheduled job validates hierarchy integrity periodically
- Validation checks:
  - No orphaned categories (parent doesn't exist)
  - No circular references in closure table
  - Materialized paths match actual hierarchy
  - Depth values are correct
  - Closure table is complete (no missing relationships)
- Validation can be triggered manually via admin API
- Validation reports discrepancies with specific category IDs
- Auto-repair mode can fix detected issues
- Validation emits metrics for monitoring
- Failed validation triggers alerts

### AC16: Bulk Hierarchy Operations

**Implementation Plan:** [AC16 - Bulk Hierarchy Operations](../plans/product-categories/AC16-bulk-hierarchy-operations.md)

- Bulk create categories from nested structure (import)
- Bulk move multiple categories to new parent
- Bulk archive category subtree
- Bulk reassign products between categories
- Operations are transactional (all or nothing)
- Operations emit single aggregate event with affected IDs
- Operations respect rate limits to prevent system overload
- Progress can be tracked for large operations
- Operations can be cancelled if in progress

### AC17: Business Rules and Validation

**Implementation Plan:** [AC17 - Business Rules and Validation](../plans/product-categories/AC17-business-rules-validation.md)

**Category Validation:**
- Name: required, 1-100 characters, unique among siblings
- Slug: required, 1-100 characters, URL-safe (lowercase, hyphens), globally unique
- Description: optional, max 2000 characters
- Display order: positive integer, unique among siblings
- Maximum hierarchy depth: 10 levels (configurable)
- Maximum children per category: 100 (configurable)

**Hierarchy Rules:**
- Root categories have null parentId and depth 0
- Child categories must have active parent
- Cannot move category to its own descendant
- Cannot delete category with assigned products (must reassign)
- Cannot delete category with children (must cascade or reassign)
- Archived categories cannot have products assigned
- Activating category requires active parent chain

**Product Assignment Rules:**
- Product must have exactly one primary category
- Product can be in unlimited categories (configurable max: 20)
- Cannot assign to archived categories
- Cannot assign non-existent products

### AC18: Resiliency and Error Handling

**Implementation Plan:** [AC18 - Resiliency and Error Handling](../plans/product-categories/AC18-resiliency-error-handling.md)

- Circuit breaker protects database operations
- Retry logic handles transient failures
- Rate limiting prevents abuse of hierarchy modification endpoints
- Bulk operations have separate, stricter rate limits
- Fallback for tree queries returns cached stale data with warning
- All errors logged with correlation IDs
- Domain exceptions mapped to HTTP responses:
  - `CategoryNotFoundException` → 404
  - `DuplicateSlugException` → 409
  - `DuplicateNameException` → 409
  - `CircularReferenceException` → 422
  - `MaxDepthExceededException` → 422
  - `CategoryHasProductsException` → 409
  - `CategoryHasChildrenException` → 409
  - `ValidationException` → 400
- Concurrent modification returns 409 with current version

### AC19: Caching Strategy

**Implementation Plan:** [AC19 - Caching Strategy](../plans/product-categories/AC19-caching-strategy.md)

- Category tree is cached with configurable TTL (default: 5 minutes)
- Individual category lookups are cached (default: 10 minutes)
- Breadcrumbs are cached per category (default: 10 minutes)
- Product counts are cached with shorter TTL (default: 1 minute)
- Cache invalidation strategies:
  - Category update: invalidate category and ancestors
  - Category move: invalidate old subtree, new subtree, and ancestors
  - Category delete: invalidate entire tree
  - Product assignment: invalidate category and ancestors (for counts)
- Cache warming on application startup for top-level categories
- Stale-while-revalidate for tree queries during cache refresh

### AC20: Observability

**Implementation Plan:** [AC20 - Observability](../plans/product-categories/AC20-observability.md)

**Tracing:**
- All command operations emit trace spans
- All query operations emit trace spans
- Hierarchy traversal operations include depth and node count
- Cache hits/misses are traced

**Metrics:**
- `category.total.count` - gauge of total categories
- `category.depth.distribution` - histogram of category depths
- `category.children.count` - histogram of children per category
- `category.products.count` - histogram of products per category
- `category.tree.query.duration` - histogram of tree query times
- `category.hierarchy.validation.errors` - counter of integrity issues
- `category.cache.hit` / `category.cache.miss` - cache effectiveness

**Logging:**
- Hierarchy modifications log before/after states
- Circular reference attempts are logged with full path
- Bulk operations log progress and completion
- All logs include correlation IDs

**Dashboards:**
- Category hierarchy overview (depth distribution, node counts)
- Hierarchy modification activity
- Query performance (tree vs single lookups)
- Cache effectiveness

### AC21: Testing

**Implementation Plan:** [AC21 - Testing](../plans/product-categories/AC21-testing.md)

**Unit Tests:**
- All command handlers tested with `StepVerifier`
- Circular reference detection tested exhaustively
- Depth calculation tested for various scenarios
- Materialized path generation tested
- Closure table updates tested

**Integration Tests:**
- Full hierarchy CRUD operations verified
- Move operations verify subtree updates
- Product assignment cascades verified
- Concurrent modification handling verified
- Large hierarchy performance tested (1000+ categories)

**Controller Tests:**
- All endpoints tested with `@WebFluxTest` and `WebTestClient`
- Request validation tested
- Error responses verified

**Hierarchy-Specific Tests:**
- Maximum depth enforcement
- Circular reference prevention at all levels
- Path reconstruction from events
- Integrity validation accuracy

**Test Coverage:**
- Minimum 80% line coverage
- 100% coverage on hierarchy logic
- Test data builders for category trees

## Future Acceptance Criteria (Phase 2)

### AC22: Category Attributes and Inheritance
- Categories can define attributes (e.g., "Brand", "Size")
- Child categories inherit parent attributes
- Inheritance can be overridden at any level
- Products in category must provide required attributes
- Attribute changes propagate to product validation

### AC23: Category Display Templates
- Categories can specify display template
- Templates control product listing layout
- Templates are inherited from parent (overridable)
- Template changes trigger cache invalidation

### AC24: Faceted Navigation Configuration
- Categories define available facets for filtering
- Facets are inherited and extensible
- Facet values are aggregated from products
- Facet counts update with product changes

### AC25: Multi-Channel Category Trees
- Multiple category hierarchies can coexist
- Each channel (web, mobile, wholesale) has its own tree
- Products can have different primary categories per channel
- Cross-channel category mapping is supported

### AC26: SEO Metadata
- Categories have SEO title, description, keywords
- Meta tags are inherited from parent (overridable)
- Canonical URLs are generated from hierarchy
- Sitemap generation includes category pages

## Definition of Done

- [ ] All Phase 1 acceptance criteria are met
- [ ] Category aggregate correctly enforces hierarchy rules
- [ ] Circular reference prevention works at all depths
- [ ] Maximum depth is enforced for all operations
- [ ] Materialized path and closure table are in sync
- [ ] Hierarchy traversal queries perform efficiently (< 50ms for 1000 nodes)
- [ ] Product assignments correctly update counts
- [ ] All REST endpoints are functional and documented
- [ ] Caching improves performance without stale data issues
- [ ] Integrity validation detects and reports issues
- [ ] Resiliency patterns are implemented and tested
- [ ] Observability is configured with dashboards available
- [ ] All tests pass with minimum coverage threshold
- [ ] Code review completed by at least one team member
- [ ] API documentation is complete and accurate
- [ ] Performance testing validates:
  - Tree query < 100ms (p99) for 10-level hierarchy
  - Breadcrumb generation < 20ms (p99)
  - Product count query < 50ms (p99)
  - Category move < 500ms (p99) for subtree of 100 nodes

## Technical Notes

### Database Schemas

```sql
-- Command Model Schema
CREATE SCHEMA IF NOT EXISTS command_model;

CREATE TABLE command_model.category (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    parent_id UUID REFERENCES command_model.category(id),
    display_order INTEGER NOT NULL DEFAULT 0,
    depth INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(parent_id, name),
    UNIQUE(parent_id, display_order),
    CONSTRAINT chk_depth CHECK (depth >= 0 AND depth <= 10),
    CONSTRAINT chk_no_self_parent CHECK (id != parent_id)
);

CREATE INDEX idx_category_parent ON command_model.category(parent_id);
CREATE INDEX idx_category_status ON command_model.category(status);
CREATE INDEX idx_category_depth ON command_model.category(depth);

-- Read Model Schema
CREATE SCHEMA IF NOT EXISTS read_model;

-- Materialized Path Read Model
CREATE TABLE read_model.category_path (
    category_id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    path_ids TEXT NOT NULL,          -- '/uuid1/uuid2/uuid3/'
    path_slugs TEXT NOT NULL,        -- '/electronics/computers/laptops/'
    path_names TEXT NOT NULL,        -- '/Electronics/Computers/Laptops/'
    depth INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    display_order INTEGER NOT NULL,
    product_count INTEGER NOT NULL DEFAULT 0,
    recursive_product_count INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_category_path_ids ON read_model.category_path USING btree (path_ids);
CREATE INDEX idx_category_path_slugs ON read_model.category_path USING btree (path_slugs);
CREATE INDEX idx_category_path_depth ON read_model.category_path(depth);

-- Closure Table Read Model
CREATE TABLE read_model.category_closure (
    ancestor_id UUID NOT NULL,
    descendant_id UUID NOT NULL,
    depth INTEGER NOT NULL,
    PRIMARY KEY (ancestor_id, descendant_id)
);

CREATE INDEX idx_closure_ancestor ON read_model.category_closure(ancestor_id);
CREATE INDEX idx_closure_descendant ON read_model.category_closure(descendant_id);
CREATE INDEX idx_closure_depth ON read_model.category_closure(depth);

-- Product-Category Assignment
CREATE TABLE read_model.product_category (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    category_id UUID NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER NOT NULL DEFAULT 0,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(product_id, category_id)
);

CREATE INDEX idx_product_category_product ON read_model.product_category(product_id);
CREATE INDEX idx_product_category_category ON read_model.product_category(category_id);
CREATE INDEX idx_product_category_primary ON read_model.product_category(product_id) WHERE is_primary;

-- Category Tree View (Materialized for performance)
CREATE MATERIALIZED VIEW read_model.category_tree AS
SELECT
    c.category_id,
    c.name,
    c.slug,
    c.path_slugs,
    c.depth,
    c.status,
    c.display_order,
    c.product_count,
    c.recursive_product_count,
    p.category_id as parent_id
FROM read_model.category_path c
LEFT JOIN read_model.category_closure cl ON cl.descendant_id = c.category_id AND cl.depth = 1
LEFT JOIN read_model.category_path p ON p.category_id = cl.ancestor_id
WHERE c.status = 'ACTIVE'
ORDER BY c.path_ids;

CREATE UNIQUE INDEX idx_category_tree_id ON read_model.category_tree(category_id);
```

### Key Dependencies

- Spring WebFlux for reactive REST endpoints
- R2DBC for reactive database access
- Jackson for JSON serialization
- Resilience4j for circuit breaker, retry, rate limiting
- Micrometer for metrics
- OpenTelemetry for distributed tracing
- Caffeine for local caching

### Package Structure

```
com.example.cqrsspike.category/
├── command/
│   ├── aggregate/
│   │   └── CategoryAggregate.kt
│   ├── handler/
│   │   ├── CategoryCommandHandler.kt
│   │   └── ProductCategoryCommandHandler.kt
│   └── model/
│       ├── CategoryCommands.kt
│       └── ProductCategoryCommands.kt
├── query/
│   ├── handler/
│   │   ├── CategoryQueryHandler.kt
│   │   ├── CategoryTreeQueryHandler.kt
│   │   └── ProductCategoryQueryHandler.kt
│   ├── model/
│   │   ├── CategoryReadModel.kt
│   │   ├── CategoryPathReadModel.kt
│   │   ├── CategoryClosureReadModel.kt
│   │   └── CategoryTreeNode.kt
│   └── projection/
│   │   ├── CategoryPathProjection.kt
│   │   ├── CategoryClosureProjection.kt
│   │   └── ProductCountProjection.kt
├── event/
│   ├── CategoryCreated.kt
│   ├── CategoryMoved.kt
│   ├── ProductAssignedToCategory.kt
│   └── ...
├── service/
│   ├── BreadcrumbService.kt
│   ├── HierarchyValidationService.kt
│   └── CircularReferenceDetector.kt
├── api/
│   ├── CategoryCommandController.kt
│   ├── CategoryQueryController.kt
│   ├── CategoryTreeController.kt
│   └── dto/
│       ├── CreateCategoryRequest.kt
│       ├── MoveCategoryRequest.kt
│       ├── CategoryResponse.kt
│       ├── CategoryTreeResponse.kt
│       ├── BreadcrumbResponse.kt
│       └── ...
└── infrastructure/
    ├── CategoryRepository.kt
    ├── CategoryPathRepository.kt
    ├── CategoryClosureRepository.kt
    └── CategoryCacheService.kt
```

### Hierarchy Algorithms

```kotlin
// Circular Reference Detection
fun detectCircularReference(categoryId: UUID, newParentId: UUID): Boolean {
    // Check if newParentId is in the descendant set of categoryId
    return closureRepository
        .findDescendants(categoryId)
        .any { it.descendantId == newParentId }
        .block() ?: false
}

// Materialized Path Update on Move
fun updatePathsAfterMove(categoryId: UUID, oldPath: String, newPath: String): Flux<CategoryPath> {
    return categoryPathRepository
        .findByPathPrefix(oldPath)
        .map { category ->
            category.copy(
                pathIds = category.pathIds.replace(oldPath, newPath),
                pathSlugs = recalculateSlugPath(category),
                pathNames = recalculateNamePath(category),
                depth = calculateDepth(category.pathIds)
            )
        }
        .flatMap { categoryPathRepository.save(it) }
}

// Closure Table Update on Move
fun updateClosureAfterMove(categoryId: UUID, oldParentId: UUID?, newParentId: UUID?): Mono<Void> {
    return Mono.zip(
        // Delete old ancestor relationships for subtree
        closureRepository.deleteAncestorRelationships(categoryId),
        // Get descendants of moved category
        closureRepository.findDescendants(categoryId).collectList()
    ).flatMap { (_, descendants) ->
        // Insert new ancestor relationships for category and all descendants
        val newAncestors = newParentId?.let {
            closureRepository.findAncestors(it).collectList().block()
        } ?: emptyList()

        val newRelationships = descendants.flatMap { descendant ->
            newAncestors.map { ancestor ->
                CategoryClosure(
                    ancestorId = ancestor.ancestorId,
                    descendantId = descendant.descendantId,
                    depth = ancestor.depth + descendant.depth + 1
                )
            }
        }
        closureRepository.saveAll(newRelationships).then()
    }
}

// Breadcrumb Generation
fun generateBreadcrumb(categoryId: UUID): Mono<BreadcrumbResponse> {
    return categoryPathRepository
        .findById(categoryId)
        .map { categoryPath ->
            val ids = categoryPath.pathIds.split("/").filter { it.isNotBlank() }
            val names = categoryPath.pathNames.split("/").filter { it.isNotBlank() }
            val slugs = categoryPath.pathSlugs.split("/").filter { it.isNotBlank() }

            BreadcrumbResponse(
                categoryId = categoryId,
                breadcrumbs = ids.mapIndexed { index, id ->
                    BreadcrumbItem(
                        id = UUID.fromString(id),
                        name = names[index],
                        slug = slugs[index],
                        depth = index,
                        url = "/" + slugs.take(index + 1).joinToString("/")
                    )
                }
            )
        }
}
```
