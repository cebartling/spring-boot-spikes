# Feature: Cart Item Management

**Epic:** E-Commerce Core
**Status:** Implemented
**Priority:** High

## User Story

**As a** shopper
**I want to** add, update, and remove items in my cart
**So that** I can control what I'm purchasing

## Description

Cart item management provides full control over the contents of a shopping cart, including adding products, adjusting quantities, applying discounts, storing item-specific metadata, and validating item availability. The system calculates totals and supports various item queries for analysis.

## Acceptance Criteria

### Adding Items
- [ ] Given a cart and product, when I add an item, then the product is added to the cart with specified quantity
- [ ] Given a cart already containing a product, when I add the same product, then the quantity is increased
- [ ] Given an item addition, when it is successful, then item pricing is captured at time of addition

### Retrieving Items
- [ ] Given a cart, when I retrieve items, then all items in the cart are returned
- [ ] Given a cart and product, when I retrieve a specific item, then that item details are returned
- [ ] Given a cart, when I count items, then the total item count is accurate

### Updating Items
- [ ] Given a cart item, when I update quantity, then the item quantity changes
- [ ] Given a cart item and discount amount, when I apply discount, then the discount is applied to the item
- [ ] Given a cart item and metadata, when I update metadata, then item-specific data is stored

### Removing Items
- [ ] Given a cart item, when I remove it, then the item is deleted from the cart
- [ ] Given a cart with items, when I clear the cart, then all items are removed

### Cart Calculations
- [ ] Given cart items, when I calculate totals, then subtotal, discounts, tax, and total are computed
- [ ] Given cart items, when I count them, then the sum of quantities is returned
- [ ] Given a cart, when calculating total, then unit prices multiplied by quantities are summed

### Item Queries
- [ ] Given cart items with discounts, when I query discounted items, then only items with discounts are returned
- [ ] Given a minimum price threshold, when I query high-value items, then items above threshold are returned
- [ ] Given a minimum quantity threshold, when I query bulk items, then items above threshold are returned

### Item Validation
- [ ] Given a cart item, when I validate availability, then product availability and stock are checked
- [ ] Given all cart items, when I validate the cart, then each item's availability is checked
- [ ] Given validation results, when items are unavailable, then specific reasons are provided

## Business Rules

1. Cart items must reference valid, existing products
2. Item quantities must be positive integers
3. Unit prices are captured at the time items are added to the cart
4. Discounts are stored in cents and reduce the item total
5. Item metadata can store flexible JSON data for customization options
6. Items from inactive products should fail validation
7. Items exceeding available stock should fail validation
8. Removing the last item from a cart leaves an empty cart (not deleted)

## Out of Scope

- Quantity limits per product
- Maximum cart value limits
- Product substitutions
- Backorder management
- Gift wrapping or special handling
- Bundled products or kits

## Technical Notes

- All monetary values stored in cents (integers)
- Cart totals calculated reactively, not pre-cached
- Item validation checks product active status and stock quantity
- Discount amounts are per-item, not cart-wide
- Metadata field supports JSONB for flexibility
