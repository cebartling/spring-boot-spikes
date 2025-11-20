# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot spike project exploring resiliency patterns, built with Kotlin and Spring WebFlux. The project demonstrates reactive programming patterns with Resilience4j circuit breakers, Apache Pulsar integration, and Spring Boot Actuator monitoring.

**Tech Stack:**
- Kotlin 1.9.25
- Spring Boot 3.5.7
- Spring WebFlux (reactive web)
- Spring Cloud Circuit Breaker with Resilience4j
- Apache Pulsar (reactive)
- Spring Boot Actuator
- Java 21
- Gradle (Kotlin DSL)

## Common Commands

### Build and Run
```bash
# Run the application
./gradlew bootRun

# Build the project
./gradlew build

# Clean build
./gradlew clean build

# Build Docker image
./gradlew bootBuildImage
```

### Testing
```bash
# Run all tests
./gradlew test

# Run tests with test runtime classpath
./gradlew bootTestRun

# Run a single test class
./gradlew test --tests "com.pintailconsultingllc.resiliencyspike.ResiliencySpikeApplicationTests"

# Run a specific test method
./gradlew test --tests "com.pintailconsultingllc.resiliencyspike.ResiliencySpikeApplicationTests.contextLoads"
```

### Development
```bash
# Compile Kotlin code
./gradlew compileKotlin

# Compile test code
./gradlew compileTestKotlin

# Run all checks (tests + other verification tasks)
./gradlew check

# View all tasks
./gradlew tasks

# View dependencies
./gradlew dependencies
```

## Code Architecture

### Package Structure
The codebase follows a standard Spring Boot Kotlin structure under the base package `com.pintailconsultingllc.resiliencyspike`:

- `src/main/kotlin/` - Main application code
- `src/main/resources/` - Application properties and configuration
- `src/test/kotlin/` - Test code

### Key Technologies and Patterns

**Reactive Stack:**
This is a fully reactive application using Spring WebFlux, not traditional Spring MVC. All HTTP handling uses reactive types (Mono/Flux) and the application runs on Netty by default, not Tomcat.

**Resilience4j Integration:**
The project uses Spring Cloud Circuit Breaker with Resilience4j for implementing resilience patterns (circuit breakers, rate limiters, retries, bulkheads, time limiters).

**Apache Pulsar:**
Reactive Pulsar integration is configured for message streaming. This is reactive messaging, so use reactive Pulsar templates and reactive consumers/producers.

**Actuator:**
Spring Boot Actuator is enabled for monitoring, health checks, and metrics. Actuator endpoints will expose information about circuit breakers, health, and application metrics.

### Important Configuration Notes

- **Java Version:** Project targets Java 21 (configured in build.gradle.kts)
- **Kotlin Configuration:** Uses strict JSR-305 compiler flags for null-safety
- **Spring Cloud Version:** 2025.0.0 (managed via BOM)
- **Test Framework:** JUnit 5 (Jupiter) with Kotlin test support

### Dependencies of Note

- `reactor-kotlin-extensions` - Kotlin-friendly extensions for Project Reactor
- `kotlinx-coroutines-reactor` - Coroutine support for reactive code
- `jackson-module-kotlin` - JSON serialization for Kotlin data classes
- `reactor-test` - Testing support for reactive streams
- `kotlinx-coroutines-test` - Testing support for coroutines

## Development Notes

When adding new features or making changes:

1. **Reactive Programming:** Use Mono/Flux for asynchronous operations, not blocking code
2. **Kotlin Coroutines:** The project supports coroutines with reactor integration
3. **Circuit Breaker:** Use Spring Cloud Circuit Breaker annotations/configurations for resilience patterns
4. **Testing:** Write reactive tests using `reactor-test` StepVerifier for Mono/Flux testing
