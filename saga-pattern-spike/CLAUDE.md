# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot 4.0 spike project exploring the **saga pattern** for distributed transactions. Built with Kotlin and reactive/coroutine support via WebFlux.

## Build Commands

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.pintailconsultingllc.sagapattern.SagapatternApplicationTests"

# Run a single test method
./gradlew test --tests "com.pintailconsultingllc.sagapattern.SagapatternApplicationTests.contextLoads"

# Run the application
./gradlew bootRun

# Clean build
./gradlew clean build
```

## Infrastructure Commands

```bash
# Start PostgreSQL and WireMock
docker compose up -d

# Stop services
docker compose down

# Reset database (destroys data)
docker compose down -v && docker compose up -d

# View logs
docker compose logs -f

# Check WireMock mappings
curl http://localhost:8081/__admin/mappings
```

## Tech Stack

- **Kotlin 2.2** with JVM 24 (Amazon Corretto)
- **Spring Boot 4.0** with WebFlux (reactive)
- **Gradle 9.2** (Kotlin DSL)
- **Coroutines** via kotlinx-coroutines-reactor
- **Jackson** for JSON serialization
- **PostgreSQL 17** for persistence (via Docker)
- **WireMock 3.9** for external service mocks (via Docker)

## Architecture

The project uses Spring WebFlux for non-blocking, reactive HTTP handling. Key patterns:

- Reactive streams with Project Reactor (`Mono`, `Flux`)
- Kotlin coroutines integration for cleaner async code
- WebClient for reactive HTTP client calls

## Documentation Structure

- `docs/features/` - Feature specifications
- `docs/implementation-plans/` - Implementation planning documents

## SDK Management

Uses SDKMAN for Java/Gradle version management. Run `sdk env` to activate configured versions (Java 24.0.2-amzn, Gradle 9.2.1).
