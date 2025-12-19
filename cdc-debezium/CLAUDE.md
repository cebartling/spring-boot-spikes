# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot 4.x spike project demonstrating Change Data Capture (CDC) using Debezium with PostgreSQL and
Kafka (KRaft mode). The application consumes CDC events from Kafka and materializes state with OpenTelemetry
observability.

## Build Commands

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.pintailconsultingllc.cdcdebezium.SomeTestClass"

# Run a single test method
./gradlew test --tests "com.pintailconsultingllc.cdcdebezium.SomeTestClass.someTestMethod"

# Run the application
./gradlew bootRun

# Clean build
./gradlew clean build
```

## Technology Stack

- **Kotlin 2.2** with Spring Boot 4.0
- **Java 24** (toolchain) / Java 24 (SDKMAN runtime)
- **Gradle 9.2** (Kotlin DSL)
- **Spring WebFlux** (reactive web)
- **R2DBC** (reactive database access with PostgreSQL)
- **Spring Kafka** (CDC event consumption)
- **OpenTelemetry** (traces, metrics, logs)

## Architecture

The application implements a CDC pipeline:

1. **PostgreSQL** with logical replication captures row-level changes
2. **Debezium PostgreSQL connector** (running in Kafka Connect) streams changes to Kafka
3. **Kafka (KRaft mode)** serves as the event backbone
4. **Spring Boot consumer** processes CDC events with idempotent upsert/delete semantics
5. **OpenTelemetry Collector** exports traces to Jaeger and metrics to Prometheus

## Key Implementation Details

- CDC events arrive as JSON (schemas disabled)
- Consumer uses manual acknowledgements with single-threaded consumption
- Deletes are handled via Debezium's rewrite mode plus Kafka tombstones
- All database operations must be idempotent

## Feature Specifications

- See `docs/features/FEATURE-001.md` for the complete CDC spike specification.
- Implementation plans are documented in `docs/implementation-plans/`. 
