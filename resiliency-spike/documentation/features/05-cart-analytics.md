# Feature: Shopping Cart Analytics

**Epic:** E-Commerce Analytics
**Status:** Implemented
**Priority:** Medium

## User Story

**As a** business analyst
**I want to** analyze cart conversion and abandonment metrics
**So that** I can measure e-commerce performance and identify improvement opportunities

## Description

Shopping cart analytics provides business intelligence on cart behavior, focusing on conversion rates, abandonment rates, and temporal analysis of cart events. Analytics leverage cart state history to compute key performance indicators over specified date ranges.

## Acceptance Criteria

### Event Analysis by Date Range
- [ ] Given a date range, when I query events, then all cart events within that range are returned
- [ ] Given a date range, when I query conversion events, then only cart conversion events are returned
- [ ] Given a date range, when I query abandonment events, then only cart abandonment events are returned

### Conversion Rate Calculation
- [ ] Given a date range, when I calculate conversion rate, then the percentage of carts that converted is returned
- [ ] Given conversion rate calculation, when displayed, then it includes total carts created and total converted
- [ ] Given no carts created in range, when I calculate conversion rate, then rate is zero or null

### Abandonment Rate Calculation
- [ ] Given a date range, when I calculate abandonment rate, then the percentage of carts that were abandoned is returned
- [ ] Given abandonment rate calculation, when displayed, then it includes total carts created and total abandoned
- [ ] Given no carts created in range, when I calculate abandonment rate, then rate is zero or null

### Metric Accuracy
- [ ] Given multiple cart events, when I calculate rates, then only unique carts are counted (not duplicate events)
- [ ] Given carts in multiple states, when calculating rates, then state transitions are tracked accurately

## Business Rules

1. Conversion rate = (Converted Carts / Total Carts Created) * 100
2. Abandonment rate = (Abandoned Carts / Total Carts Created) * 100
3. Date ranges are inclusive of start and end dates
4. Only carts created within the date range are included in denominators
5. A cart can only be counted once per metric (final state)
6. Rates are returned as percentages (0-100)

## Out of Scope

- Revenue analytics
- Average cart value
- Time to conversion metrics
- Cohort analysis
- Funnel analysis
- Attribution analysis
- Predictive analytics
- A/B testing support

## Technical Notes

- Analytics queries leverage cart_state_history table
- Date range queries use indexed timestamp columns
- Calculations performed reactively with Mono/Flux
- Rates computed as doubles for precision
- ISO 8601 date formats for API requests
