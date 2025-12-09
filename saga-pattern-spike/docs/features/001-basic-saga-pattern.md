# Feature: Basic Saga Pattern

## Overview

Implement a basic saga pattern to coordinate multi-step business operations that span multiple services, ensuring data consistency through compensating actions when failures occur.

## User Stories

### US-001: Complete a Multi-Step Order Process

**As a** customer
**I want** my order to be processed through all required steps (inventory reservation, payment, shipping)
**So that** I receive confirmation only when my entire order is successfully completed

#### Acceptance Criteria

- Given I place an order with valid items and payment information
- When all steps in the order process complete successfully
- Then I receive a single confirmation that my order is complete
- And all related records reflect the completed state

---

### US-002: Automatic Rollback on Failure

**As a** customer
**I want** my order to be automatically cancelled if any step fails
**So that** I am not charged for items that cannot be fulfilled

#### Acceptance Criteria

- Given I place an order that begins processing
- When any step in the order process fails (e.g., payment declined, item out of stock)
- Then all previously completed steps are automatically reversed
- And I receive notification of the failure with a clear reason
- And no partial charges or reservations remain

---

### US-003: View Order Status During Processing

**As a** customer
**I want** to see the current status of my order while it is being processed
**So that** I know which stage my order is in

#### Acceptance Criteria

- Given I have placed an order
- When I check my order status
- Then I can see which step is currently in progress
- And I can see which steps have completed
- And I can see if any step has failed

---

### US-004: Retry Failed Orders

**As a** customer
**I want** to retry a failed order without re-entering all my information
**So that** I can easily complete my purchase after resolving the issue

#### Acceptance Criteria

- Given my order failed due to a recoverable issue (e.g., insufficient funds)
- When I choose to retry the order after resolving the issue
- Then the order process resumes from the failed step
- And previously successful steps are not repeated unnecessarily

---

### US-005: Order History Includes Saga Details

**As a** customer
**I want** to see the full history of my order processing
**So that** I understand what happened if something went wrong

#### Acceptance Criteria

- Given I have an order (completed or failed)
- When I view the order details
- Then I can see a timeline of all processing steps
- And each step shows its outcome (success, failed, compensated)
- And failed steps include the reason for failure

---

## Out of Scope

- Manual intervention workflows for failed sagas
- Parallel step execution within a saga
- Saga timeout and escalation policies
- Cross-system saga coordination (external services)

## Success Metrics

- Orders that fail mid-process are fully rolled back with no orphaned state
- Customers can track order progress in real-time
- Failed orders can be retried without data loss
