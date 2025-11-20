# Resiliency Spike

A Spring Boot spike project exploring resiliency patterns using Kotlin, Spring WebFlux, Resilience4j, and Apache Pulsar.

## Overview

This project demonstrates reactive programming patterns with circuit breakers, rate limiting, and other resilience patterns in a modern Spring Boot application.

## Tech Stack

- **Language:** Kotlin 1.9.25
- **Framework:** Spring Boot 3.5.7
- **Web:** Spring WebFlux (reactive)
- **Messaging:** Apache Pulsar (reactive)
- **Resilience:** Spring Cloud Circuit Breaker with Resilience4j
- **Monitoring:** Spring Boot Actuator
- **Database:** PostgreSQL 18
- **Build Tool:** Gradle (Kotlin DSL)
- **Java Version:** 21

## Getting Started

### Prerequisites

- Java 21
- Docker and Docker Compose
- Gradle (wrapper included)

### Local Development Setup

1. **Start infrastructure services:**
   ```bash
   docker-compose up -d
   ```

   This starts:
   - **Apache Pulsar** on ports 6650 (broker) and 8080 (admin)
   - **PostgreSQL** on port 5432
   - **Database Init Container** (one-shot) to initialize the schema

2. **Verify services are healthy:**
   ```bash
   docker-compose ps
   ```

3. **Run the application:**
   ```bash
   ./gradlew bootRun
   ```

4. **Run tests:**
   ```bash
   ./gradlew test
   ```

### Docker Services

#### Apache Pulsar
- **Broker Port:** 6650 (for application connections)
- **Admin Port:** 8080 (web console and admin API)
- **Mode:** Standalone
- **Data:** Persisted in `pulsar-data` volume

#### PostgreSQL
- **Port:** 5432
- **Database:** `resiliency_spike`
- **Username:** `resiliency_user`
- **Password:** `resiliency_password`
- **Data:** Persisted in `postgres-data` volume
- **Schema Initialization:** Automatic via one-shot init container

#### Database Schema
The database schema is automatically initialized on first startup by the `db-init` container. SQL scripts in `docker/init-scripts/` are executed in alphabetical order.

**Initialized Tables:**
- `resilience_events` - Tracks resilience events (circuit breaker, rate limiter, etc.)
- `circuit_breaker_state` - Stores circuit breaker state and metrics
- `rate_limiter_metrics` - Rate limiter statistics

To modify the schema:
1. Add or edit SQL files in `docker/init-scripts/`
2. Restart with clean volumes: `docker-compose down -v && docker-compose up -d`

### Useful Commands

#### Application
```bash
# Build the project
./gradlew build

# Run all tests
./gradlew test

# Run a specific test
./gradlew test --tests "com.pintailconsultingllc.resiliencyspike.ResiliencySpikeApplicationTests"

# Clean build
./gradlew clean build

# Build Docker image
./gradlew bootBuildImage
```

#### Docker Compose
```bash
# Start services
docker-compose up -d

# View logs
docker-compose logs -f

# View logs for specific service
docker-compose logs -f pulsar
docker-compose logs -f postgres
docker-compose logs db-init  # View schema initialization logs

# Stop services
docker-compose down

# Stop and remove volumes (clean slate)
docker-compose down -v
```

## Architecture

This is a fully **reactive application** using Spring WebFlux:
- HTTP handling uses reactive types (Mono/Flux)
- Runs on Netty, not Tomcat
- Uses Project Reactor for reactive streams
- Supports Kotlin coroutines with reactor integration

### Key Components

- **Resilience4j** - Circuit breakers, rate limiters, retries, bulkheads, and time limiters
- **Apache Pulsar** - Reactive messaging and event streaming
- **Spring Boot Actuator** - Health checks, metrics, and monitoring endpoints
- **WebFlux** - Reactive REST APIs

## Development Notes

- All HTTP operations should use `Mono<T>` or `Flux<T>` return types
- Use `reactor-test` and `StepVerifier` for testing reactive streams
- Leverage Kotlin coroutines with `kotlinx-coroutines-reactor` for cleaner async code
- Circuit breaker metrics available through Actuator endpoints

## Resources

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/3.5.7/reference/)
- [Spring WebFlux](https://docs.spring.io/spring-framework/reference/6.2.12/web/webflux.html)
- [Resilience4j](https://resilience4j.readme.io/)
- [Apache Pulsar](https://pulsar.apache.org/docs/)
- [Project Reactor](https://projectreactor.io/docs/core/release/reference/)
