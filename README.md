# spring-boot-spikes

Various Spring Boot spike solutions for learning.
Many of these are architectural patterns found [here](https://learn.microsoft.com/en-us/azure/architecture/patterns/).

## Spike Solutions

- [Resiliency Spike](#resiliency-spike)
- [CQRS Spike](#cqrs-spike)
- [Saga Pattern Spike](#saga-pattern-spike)
- [CDC Debezium Spike](#cdc-debezium-spike)

---

## Resiliency Spike

**[View Project](./resiliency-spike)**

### Synopsis

A comprehensive Spring Boot spike project exploring enterprise-grade resiliency patterns and reactive programming. Implements a fully-functional e-commerce platform (product catalog, inventory management, and shopping cart) demonstrating production-ready fault tolerance patterns, high-performance reactive architecture, and distributed observability.

### Technologies

| Category | Technology |
|----------|------------|
| Resilience | Resilience4j (circuit breakers, rate limiters, retries) |
| Reactive | Spring WebFlux, Project Reactor, Netty |
| Messaging | Apache Pulsar (reactive) |
| Database | Spring Data R2DBC, PostgreSQL 18 |
| Observability | OpenTelemetry, Micrometer, Jaeger, SigNoz |
| Secrets | HashiCorp Vault |
| API Docs | OpenAPI 3.0, Swagger UI |
| Infrastructure | Docker, Docker Compose |

### Details

| Attribute | Value |
|-----------|-------|
| Language | Kotlin 1.9.25 |
| Spring Boot | 3.5.7 |
| Spring Cloud | 2025.0.0 |
| Last Updated | December 22, 2025 |

---

## CQRS Spike

**[View Project](./cqrs-spike)**

### Synopsis

A demonstration of the CQRS (Command Query Responsibility Segregation) and Event Sourcing architectural patterns. Implements a complete Product Catalog system showcasing reactive, event-driven architecture with separate command and query models, event store persistence, and comprehensive observability.

### Technologies

| Category | Technology |
|----------|------------|
| Reactive | Spring WebFlux (non-blocking I/O) |
| Database | Spring Data R2DBC, PostgreSQL 18, Flyway |
| Resilience | Resilience4j (circuit breaker, retry, rate limiter) |
| Secrets | HashiCorp Vault, Spring Cloud Vault Config |
| Observability | OpenTelemetry, Micrometer, Prometheus, Grafana, Loki, Tempo |
| Testing | JUnit 5, Mockito, Cucumber BDD, Reactor Test |
| API Docs | SpringDoc OpenAPI, Swagger |
| Build | Gradle (Kotlin DSL) |
| Infrastructure | Docker Compose |

### Details

| Attribute | Value |
|-----------|-------|
| Language | Kotlin 2.2.21 |
| Spring Boot | 4.0.0 |
| Last Updated | December 2025 |

---

## Saga Pattern Spike

**[View Project](./saga-pattern-spike)**

### Synopsis

A Spring Boot spike project demonstrating the saga orchestration pattern for managing distributed transactions across multiple services. Implements a complete e-commerce order processing workflow with automatic compensation (rollback) when any step fails, featuring real-time status tracking via Server-Sent Events (SSE), retry support, order history, and comprehensive observability.

### Technologies

| Category | Technology |
|----------|------------|
| Reactive | Spring WebFlux, Project Reactor, Kotlin Coroutines |
| Database | Spring Data R2DBC, PostgreSQL |
| Tracing | OpenTelemetry, Jaeger |
| Metrics | Micrometer, Prometheus, Grafana |
| Logging | Loki |
| Secrets | HashiCorp Vault, Spring Cloud Vault |
| Testing | Cucumber 7.20, TestContainers, Kotest, WireMock 3.9 |
| Load Testing | k6 |
| Build | Gradle 9.2 (Kotlin DSL) |
| Infrastructure | Docker Compose |

### Details

| Attribute | Value |
|-----------|-------|
| Language | Kotlin 2.2 |
| Spring Boot | 4.0.0 |
| JVM | Java 24 (Amazon Corretto) |
| Last Updated | December 15, 2025 |

---

## CDC Debezium Spike

**[View Project](./cdc-debezium)**

### Synopsis

A Spring Boot spike project demonstrating Change Data Capture (CDC) using Debezium with PostgreSQL and Kafka (KRaft mode). Implements a complete CDC pipeline that captures row-level database changes from PostgreSQL using logical replication, streams them to Kafka via the Debezium connector, and consumes CDC events in a Spring Boot reactive application with idempotent processing. Includes chaos engineering tools for resilience testing.

### Technologies

| Category | Technology |
|----------|------------|
| Reactive | Spring WebFlux |
| Source Database | PostgreSQL 16 (R2DBC) |
| Target Database | MongoDB 8.x (materialized view) |
| Messaging | Apache Kafka (KRaft mode), Debezium PostgreSQL Connector |
| Observability | OpenTelemetry, Grafana LGTM Stack (Loki, Tempo, Prometheus) |
| Testing | JUnit 5, MockK, Cucumber JVM, Awaitility, Playwright |
| Load Testing | k6 (with xk6-sql, xk6-mongo, xk6-output-prometheus-remote) |
| Chaos Engineering | Pumba, Toxiproxy |
| Build | Gradle 9.2 (Kotlin DSL) |
| Infrastructure | Docker, Docker Compose |

### Details

| Attribute | Value |
|-----------|-------|
| Language | Kotlin 2.2 |
| Spring Boot | 4.0.1 |
| JVM | Java 24 |
| Last Updated | December 26, 2025 |

