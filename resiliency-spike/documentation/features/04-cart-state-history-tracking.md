# Feature: Cart State History Tracking

**Epic:** E-Commerce Analytics
**Status:** Implemented
**Priority:** Medium

## User Story

**As a** business analyst
**I want to** track all state changes and events for shopping carts
**So that** I can understand customer behavior and cart lifecycle

## Description

Cart state history provides complete event tracking and audit trail for shopping cart lifecycle events. Every significant state change is recorded with timestamps, event types, old and new states, and metadata. This enables reconstruction of cart history, behavioral analysis, and debugging.

## Acceptance Criteria

### Event Recording
- [ ] Given a cart state change, when it occurs, then an event record is created
- [ ] Given an event, when it is recorded, then event type, old state, new state, and timestamp are captured
- [ ] Given an event, when it is recorded, then optional metadata can be included

### Event Retrieval
- [ ] Given a cart, when I retrieve history, then all events for that cart are returned in chronological order
- [ ] Given a cart and time period, when I retrieve recent events, then events from the last N hours are returned
- [ ] Given a cart and event type, when I filter events, then only events of that type are returned
- [ ] Given a cart, when I retrieve the latest event, then the most recent event is returned

### Event Counting
- [ ] Given a cart and event type, when I count events, then the accurate count is returned
- [ ] Given a cart, when I count total events, then all events are counted

### Activity Summary
- [ ] Given a cart, when I request activity summary, then event counts by type are returned
- [ ] Given an activity summary, when displayed, then total event count is included

## Business Rules

1. Events are immutable once created
2. Events are recorded chronologically with precise timestamps
3. Event types include: CREATED, ITEM_ADDED, ITEM_REMOVED, STATUS_CHANGED, ABANDONED, CONVERTED, EXPIRED, RESTORED
4. Old state and new state capture the cart status before and after the event
5. All events include the cart ID as a foreign key
6. Events persist even if the cart is deleted (for audit purposes)

## Out of Scope

- Event replay or cart reconstruction
- Event-based notifications
- Real-time event streaming
- Event aggregation or rollup
- Custom event types

## Technical Notes

- Events stored in separate table from carts
- JSONB metadata field allows flexible event context
- Indexed on cart_id and created_at for efficient queries
- Event types are enumerated values
- Timezone-aware timestamps using OffsetDateTime
