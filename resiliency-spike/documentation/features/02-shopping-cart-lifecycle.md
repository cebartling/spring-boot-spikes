# Feature: Shopping Cart Lifecycle Management

**Epic:** E-Commerce Core
**Status:** Implemented
**Priority:** High

## User Story

**As a** shopper
**I want to** maintain a shopping cart across my session
**So that** I can collect items before completing my purchase

## Description

Shopping cart lifecycle management handles the complete journey of a cart from creation through various states including active, abandoned, converted, and expired. Carts can be associated with sessions or authenticated users, tracked over time, and managed through automated processes.

## Acceptance Criteria

### Cart Creation
- [ ] Given a session, when I create a cart, then a new empty cart is created with a unique identifier
- [ ] Given a session and optional user, when I create a cart, then it is associated with the session and user
- [ ] Given an expiration time, when I create a cart, then it expires at the specified time
- [ ] Given a session with no existing cart, when I request a cart, then a new one is created automatically

### Cart Retrieval
- [ ] Given a cart ID, when I retrieve the cart, then I receive complete cart details
- [ ] Given a cart UUID, when I retrieve by UUID, then the cart is found
- [ ] Given a session ID, when I retrieve by session, then the active cart for that session is returned
- [ ] Given a user ID, when I retrieve carts, then all carts for that user are returned
- [ ] Given a cart status, when I filter by status, then only carts with that status are returned

### Cart Association
- [ ] Given an anonymous cart and user login, when I associate the cart with user, then the cart is linked to the user account
- [ ] Given a cart and new expiration time, when I update expiration, then the cart expiration is extended

### Cart State Transitions
- [ ] Given an active cart and user inactivity, when cart is abandoned, then status changes to ABANDONED
- [ ] Given a cart at checkout completion, when cart is converted, then status changes to CONVERTED
- [ ] Given a cart past expiration time, when cart is expired, then status changes to EXPIRED
- [ ] Given an abandoned or expired cart, when cart is restored, then status returns to ACTIVE

### Cart Queries
- [ ] Given carts past expiration time, when I query expired carts, then all expired carts are returned
- [ ] Given inactive carts for specified hours, when I query abandoned carts, then matching carts are returned
- [ ] Given carts with items, when I filter carts with items, then only non-empty carts are returned
- [ ] Given empty carts, when I query them, then carts with no items are returned

### Cart Processing
- [ ] Given expired carts, when I process them, then all are marked as expired and count is returned
- [ ] Given abandoned carts threshold, when I process abandoned carts, then matching carts are marked and count is returned

### Cart Statistics
- [ ] Given all carts, when I request statistics, then counts by status (active, abandoned, converted, expired) are returned
- [ ] Given a cart status, when I count carts, then accurate count is returned

### Cart Deletion
- [ ] Given a cart ID, when I delete a cart, then it is permanently removed from the system

## Business Rules

1. A cart can only be in one status at a time: ACTIVE, ABANDONED, CONVERTED, or EXPIRED
2. Abandoned carts are identified by inactivity period (default 24 hours)
3. Expired carts are identified by the expiration timestamp
4. Anonymous carts can be associated with a user upon login
5. Cart expiration times can be extended but not reduced
6. Converted carts cannot be restored to active status
7. Each session should have at most one active cart

## Out of Scope

- Cart merging (combining multiple carts)
- Cart sharing between users
- Saved carts or wish lists
- Cart recovery emails
- Guest checkout (anonymous purchase completion)

## Technical Notes

- Carts use internal sequential IDs and external UUIDs
- Session IDs are managed by the application layer
- Cart totals are calculated from cart items in real-time
- State transitions are recorded in cart state history
- Bulk processing operations return counts for monitoring
