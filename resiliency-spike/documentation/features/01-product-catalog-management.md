# Feature: Product Catalog Management

**Epic:** E-Commerce Core
**Status:** Implemented
**Priority:** High

## User Story

**As a** catalog manager
**I want to** manage products in the catalog
**So that** customers can browse and purchase available products

## Description

The product catalog provides comprehensive management of products including creation, updates, searching, categorization, inventory tracking, and lifecycle management. Products can be organized into categories, filtered by various criteria, and activated or deactivated as needed.

## Acceptance Criteria

### Product Creation
- [ ] Given product details, when I create a product, then it is saved with a unique identifier and SKU
- [ ] Given required fields (SKU, name, category, price), when I create a product, then all required data is validated
- [ ] Given a new product, when it is created, then it defaults to active status with initial stock quantity

### Product Retrieval
- [ ] Given a product ID, when I retrieve a product, then I receive the complete product details
- [ ] Given a SKU, when I search by SKU, then I find the matching product
- [ ] Given active-only filter, when I list products, then only active products are returned
- [ ] Given a category, when I filter by category, then only products in that category are returned
- [ ] Given a price range, when I filter products, then only products within that range are returned
- [ ] Given a search term, when I search by name, then matching products are returned

### Product Updates
- [ ] Given existing product details, when I update a product, then changes are persisted
- [ ] Given a product and new stock quantity, when I update stock, then the quantity is updated
- [ ] Given a product update, when it is saved, then the updated timestamp reflects the change

### Product Lifecycle
- [ ] Given an active product, when I deactivate it, then it is no longer available for sale
- [ ] Given an inactive product, when I activate it, then it becomes available for sale
- [ ] Given a product, when I delete it permanently, then it is removed from the catalog

### Inventory Monitoring
- [ ] Given a stock threshold, when I query low stock products, then products below the threshold are returned
- [ ] Given a product, when stock reaches zero, then it should be flagged appropriately

### Product Counting
- [ ] Given a category, when I count products, then the accurate count is returned
- [ ] Given active products only, when I count them, then only active products are counted

## Business Rules

1. Every product must have a unique SKU
2. Product prices must be non-negative
3. Stock quantities must be non-negative integers
4. Deactivated products remain in the database but are not available for sale
5. Products must be associated with a valid category
6. Product metadata can store flexible JSON data for extended attributes

## Out of Scope

- Price history tracking
- Product variants (size, color)
- Multi-currency pricing
- Product reviews or ratings
- Related products or recommendations

## Technical Notes

- Products support JSONB metadata for extensibility
- Soft delete via activation status (not hard delete in production)
- Category relationships are maintained via foreign keys
- Stock quantity is managed independently from inventory reservations
