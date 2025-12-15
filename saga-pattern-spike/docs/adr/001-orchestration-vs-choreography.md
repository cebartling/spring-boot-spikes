# ADR-001: Orchestration-Based Saga Pattern

## Status

Accepted

## Context

When implementing the saga pattern for distributed transactions, there are two primary approaches:

1. **Choreography**: Each service publishes events and subscribes to events from other services. Services react autonomously to events, with no central coordinator.

2. **Orchestration**: A central orchestrator coordinates the saga, explicitly calling each service in sequence and handling failures.

Our order processing system needs to coordinate multiple external services:
- Inventory service (reserve items)
- Payment service (authorize payment)
- Shipping service (schedule delivery)
- Notification service (send confirmations)

Each service can fail independently, and failures require compensating actions to maintain consistency.

## Decision

We chose the **orchestration-based** approach for our saga implementation.

Key implementation choices:
- `OrderSagaOrchestrator` serves as the central coordinator
- Steps are defined as ordered `SagaStep` implementations
- The orchestrator manages step execution, failure detection, and compensation triggering
- `CompensationOrchestrator` handles the reverse order compensation of completed steps

## Consequences

### Positive

- **Clear control flow**: The orchestrator explicitly defines the execution order, making the business process easy to understand and debug.

- **Centralized error handling**: All failure scenarios are handled in one place, simplifying compensation logic.

- **Easier testing**: The orchestrator can be unit tested with mocked services, and integration tests have a clear entry point.

- **Explicit dependencies**: Service dependencies are clearly visible in the orchestrator, rather than hidden in event subscriptions.

- **Simpler monitoring**: Saga progress and failures can be tracked through the orchestrator's execution records.

### Negative

- **Single point of coordination**: The orchestrator must be highly available; if it fails, sagas cannot progress.

- **Potential coupling**: The orchestrator has knowledge of all participating services, which could lead to tight coupling if not carefully designed.

- **Scalability considerations**: All saga traffic flows through the orchestrator, which may become a bottleneck.

### Mitigations

- **High availability**: The orchestrator is stateless; saga state is persisted in the database, allowing any instance to resume failed sagas.

- **Interface abstraction**: Services are accessed through abstract `SagaStep` interface, decoupling the orchestrator from service implementations.

- **Horizontal scaling**: Multiple orchestrator instances can run concurrently; database constraints prevent duplicate processing.

## Alternatives Considered

### Choreography Pattern

Rejected because:
- Complex event chains are harder to visualize and debug
- Compensation logic would be distributed across services
- Testing requires setting up complete event infrastructure
- Event ordering and duplicate handling adds complexity

### Hybrid Approach

Considered using choreography for simple flows and orchestration for complex ones, but rejected due to:
- Increased cognitive load maintaining two patterns
- Harder to establish team conventions
- Our flows are consistently complex enough to warrant orchestration

## References

- [Microsoft - Saga Pattern](https://learn.microsoft.com/en-us/azure/architecture/reference-architectures/saga/saga)
- [Microservices.io - Saga Pattern](https://microservices.io/patterns/data/saga.html)
- [Chris Richardson - Saga Pattern](https://chrisrichardson.net/post/sagas/2019/08/04/developing-sagas-part-1.html)
