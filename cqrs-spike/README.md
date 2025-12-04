# CQRS Spike - Event Sourcing with Spring Boot

A demonstration of CQRS (Command Query Responsibility Segregation) and Event Sourcing patterns using Spring Boot 4.0, WebFlux, R2DBC, PostgreSQL 18, and HashiCorp Vault.

## Quick Start

### Prerequisites

- Docker Desktop (with Docker Compose)
- Java 21
- Gradle 8+

### First Time Setup

```bash
# Initialize environment file
make init-env

# Start all infrastructure services
make start

# Build the application
make build
```

### Daily Development

```bash
# Start infrastructure
make start

# Check service health
make health

# Run the application
./gradlew bootRun
```

## Service Connection Details

### Local Development URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| Vault UI | http://localhost:8200/ui | Token: `dev-root-token` |
| Vault API | http://localhost:8200 | Token: `dev-root-token` |
| PostgreSQL | localhost:5432 | User: `cqrs_user`, Password: `local_dev_password`, DB: `cqrs_db` |
| Grafana UI | http://localhost:3000 | Anonymous access enabled (Admin role) |
| Prometheus | http://localhost:9090 | N/A |
| Application | http://localhost:8080 | N/A |
| Health Check | http://localhost:8080/actuator/health | N/A |
| Debug Port | localhost:5005 | JDWP |

### Internal Service URLs (Docker Network)

| Service | Internal URL | Used By |
|---------|--------------|---------|
| Vault | http://vault:8200 | Application |
| PostgreSQL | postgres:5432 | Application |
| Prometheus | http://prometheus:9090 | Grafana |
| Loki | http://loki:3100 | Grafana, Promtail |
| Tempo | http://tempo:3200 | Grafana |

### Database Connection Strings

**JDBC:**
```
jdbc:postgresql://localhost:5432/cqrs_db
```

**psql CLI (from host):**
```bash
psql -h localhost -p 5432 -U cqrs_user -d cqrs_db
# Password: local_dev_password
```

**psql via Docker:**
```bash
docker exec -it cqrs-postgres psql -U cqrs_user -d cqrs_db
```

**Using Makefile:**
```bash
make shell-postgres
```

### Vault Access

**CLI:**
```bash
export VAULT_ADDR='http://localhost:8200'
export VAULT_TOKEN='dev-root-token'
vault status
vault kv get secret/cqrs-spike/database
```

**UI:**
Navigate to http://localhost:8200/ui and login with token: `dev-root-token`

**Using Makefile:**
```bash
make shell-vault
```

### Grafana Access

Grafana provides a unified observability UI for metrics, logs, and traces.

**UI:**
Navigate to http://localhost:3000

**Authentication:**
Anonymous access is enabled with Admin role - no login required for local development.

If you need to log in (e.g., after disabling anonymous access):
- Username: `admin`
- Password: `admin` (you'll be prompted to change on first login)

**Pre-configured Data Sources:**

| Data Source | Type | Purpose |
|-------------|------|---------|
| Prometheus | Metrics | Application and JVM metrics from `/actuator/prometheus` |
| Loki | Logs | Aggregated container logs with trace correlation |
| Tempo | Traces | Distributed tracing with trace-to-logs linking |

**Pre-configured Dashboards:**

| Dashboard | Description |
|-----------|-------------|
| CQRS Spike - Application Metrics | Main application dashboard with key metrics |

**Dashboard Panels:**

The "CQRS Spike - Application Metrics" dashboard includes:

| Panel | Description |
|-------|-------------|
| Request Rate | HTTP requests per second by method and URI |
| Response Time (p95) | 95th percentile response latency |
| JVM Memory | Heap and non-heap memory usage by area |
| Database Connections | HikariCP connection pool status (active, idle, total) |
| JVM Threads | Live and daemon thread counts |
| CPU Usage | System and process CPU utilization |

**Exploring Traces:**

1. Go to **Explore** in the left sidebar
2. Select **Tempo** as the data source
3. Use TraceQL to search for traces, e.g., `{service.name="cqrs-spike"}`
4. Click on a trace to see the full span timeline
5. Click "Logs for this span" to see correlated logs in Loki

**Exploring Logs:**

1. Go to **Explore** in the left sidebar
2. Select **Loki** as the data source
3. Use LogQL to filter logs, e.g., `{container="cqrs-spike"} |= "error"`
4. Click on a log line with a trace_id to jump to the trace in Tempo

## Common Tasks

### Infrastructure Management

- `make start` - Start all infrastructure services (Vault, PostgreSQL, Grafana, Prometheus, Loki, Tempo)
- `make stop` - Stop all services
- `make restart` - Restart all services
- `make clean` - Stop services and remove volumes (fresh start)
- `make health` - Check service health status
- `make ps` - Show running services

### Application Development

- `make build` - Build application (skip tests)
- `make test` - Run all tests
- `./gradlew bootRun` - Run application locally
- `./gradlew bootRun --args='--spring.profiles.active=local'` - Run with local profile

### Viewing Logs

- `make logs` - View all service logs (follow mode)
- `make logs-vault` - View Vault logs only
- `make logs-postgres` - View PostgreSQL logs only
- `./scripts/logs.sh [service]` - Advanced log viewing with filters

### Database Operations

- `make shell-postgres` - Open psql shell
- `./scripts/db-query.sh "SELECT * FROM ..."` - Run a query
- `./scripts/reset-service.sh postgres` - Reset database (WARNING: deletes all data)

### Vault Operations

- `make shell-vault` - Open Vault shell
- `./scripts/vault-get.sh secret/cqrs-spike/database` - Get a secret
- `./scripts/reset-service.sh vault` - Reset Vault

## Project Structure

```
cqrs-spike/
├── src/main/kotlin/com/example/cqrs/
│   ├── config/              # Configuration classes
│   ├── controller/          # REST controllers (WebFlux)
│   ├── domain/             # Domain models and aggregates
│   ├── dto/                # Data Transfer Objects
│   ├── exception/          # Custom exceptions
│   ├── infrastructure/     # Infrastructure concerns
│   │   ├── database/       # R2DBC configuration
│   │   └── vault/          # Vault integration
│   ├── repository/         # R2DBC repositories
│   └── service/            # Business logic services
├── src/main/resources/
│   ├── application.yml     # Main configuration
│   ├── application-local.yml  # Local dev overrides
│   └── db/migration/       # Flyway migrations
├── scripts/                # Helper scripts
├── docker-compose.yml      # Infrastructure services
├── .env.example           # Environment variables template
└── Makefile               # Common commands

Database Schemas:
├── event_store            # Event sourcing events
├── read_model            # Query/read side
└── command_model         # Command/write side
```

## Technology Stack

- **Language:** Kotlin 2.2.21
- **Framework:** Spring Boot 4.0.0
- **Reactive:** Spring WebFlux (NOT MVC)
- **Database:** PostgreSQL 18
- **Data Access:** R2DBC (NOT JPA/Hibernate)
- **Secrets Management:** HashiCorp Vault
- **Migrations:** Flyway
- **Resilience:** Resilience4j
- **Observability:** OpenTelemetry, Grafana, Prometheus, Loki, Tempo
- **Testing:** JUnit 5, Mockito, StepVerifier, Cucumber BDD

## Architecture

This project demonstrates:

- **CQRS Pattern:** Separate command and query models
- **Event Sourcing:** All state changes captured as immutable events
- **Reactive Streams:** Non-blocking I/O using Project Reactor
- **Schema Separation:** Distinct schemas for event store, read model, and command model
- **Secret Management:** Vault integration for sensitive configuration
- **Resilience:** Circuit breakers, retries, and rate limiting

## Development Workflow

### Running Tests

All tests (unit, integration, and acceptance) require the Docker Compose infrastructure to be running.

#### Prerequisites

1. **Docker Desktop** must be installed and running
2. **Java 21+** installed
3. **Docker Compose infrastructure** must be started before running tests

#### Starting Test Infrastructure

```bash
# Start all infrastructure services (required before running tests)
make start

# Verify services are healthy
make health
```

#### Running Tests

```bash
# Run all tests (unit, integration, and acceptance)
make test
# or
./gradlew test

# Run specific test
./gradlew test --tests "ProductServiceTest"

# Run only unit tests (no database required)
./gradlew test --tests '*Test' --exclude-task '*IntegrationTest*' --exclude-task '*AcceptanceTestRunner*'

# With coverage
./gradlew test jacocoTestReport
```

#### Stopping Test Infrastructure

```bash
# Stop services after testing (manual intervention required)
make stop

# Or for a complete reset (removes all data)
make clean
```

### Running Acceptance Tests

The project includes Cucumber BDD acceptance tests that validate complete user scenarios for the Product Catalog CQRS system. These tests use Gherkin feature files to describe behavior in business-readable language.

**IMPORTANT:** Before running acceptance tests, ensure Docker Compose infrastructure is running:

```bash
make start
make health  # Verify all services are healthy
```

#### Running All Acceptance Tests

```bash
# Run all acceptance tests
./gradlew test --tests '*AcceptanceTestRunner*'

# Or run all tests which includes acceptance tests
./gradlew test
```

#### Running Tests by Tag

Acceptance tests are tagged for selective execution:

```bash
# Run smoke tests only (quick validation)
./gradlew test --tests '*AcceptanceTestRunner*' -Dcucumber.filter.tags="@smoke"

# Run happy path tests
./gradlew test --tests '*AcceptanceTestRunner*' -Dcucumber.filter.tags="@happy-path"

# Run error handling tests
./gradlew test --tests '*AcceptanceTestRunner*' -Dcucumber.filter.tags="@error-handling"

# Run product lifecycle tests
./gradlew test --tests '*AcceptanceTestRunner*' -Dcucumber.filter.tags="@product-lifecycle"

# Run product query tests
./gradlew test --tests '*AcceptanceTestRunner*' -Dcucumber.filter.tags="@product-queries"

# Exclude work-in-progress tests
./gradlew test --tests '*AcceptanceTestRunner*' -Dcucumber.filter.tags="not @wip"

# Combine tags (AND logic)
./gradlew test --tests '*AcceptanceTestRunner*' -Dcucumber.filter.tags="@smoke and @happy-path"
```

#### Running Observability Tests

The observability acceptance tests validate the monitoring and diagnostics capabilities of the system:

```bash
# Run all observability tests
./gradlew test --tests '*AcceptanceTestRunner*' -Dcucumber.filter.tags="@observability"

# Run observability smoke tests only
./gradlew test --tests '*AcceptanceTestRunner*' -Dcucumber.filter.tags="@observability and @smoke"
```

**Observability Test Scenarios:**

| Category | Test Scenarios |
|----------|----------------|
| Health Endpoints | System status verification, component details (db, diskSpace) |
| Prometheus Metrics | Endpoint accessibility, JVM metrics, HTTP server metrics |
| Custom Metrics | Product command metrics, product query metrics recording |
| Actuator Metrics | Metrics list availability, individual metric retrieval |
| Correlation IDs | ID propagation in success responses, ID propagation in error responses |
| System Info | Application info endpoint availability |

**Endpoints Tested:**

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | System health status and component details |
| `/actuator/prometheus` | Prometheus-format metrics for scraping |
| `/actuator/metrics` | List of available metrics |
| `/actuator/metrics/{name}` | Individual metric details |
| `/actuator/info` | Application information |

#### Available Tags

| Tag | Description |
|-----|-------------|
| `@smoke` | Quick validation tests for critical paths |
| `@happy-path` | Main success scenarios |
| `@error-handling` | Error and exception scenarios |
| `@product-lifecycle` | Product creation, update, activation, discontinuation |
| `@product-queries` | Query, search, pagination, filtering |
| `@business-rules` | Business rule validation |
| `@event-sourcing` | Event store verification |
| `@observability` | Observability platform (health, metrics, correlation IDs) |
| `@edge-case` | Edge case and boundary scenarios |
| `@wip` | Work in progress (excluded by default) |

#### Viewing Test Reports

After running tests, Cucumber generates HTML and JSON reports:

```bash
# HTML report (open in browser)
open build/reports/cucumber/cucumber-report.html

# JSON report (for CI/CD integration)
cat build/reports/cucumber/cucumber-report.json
```

#### Feature Files Location

Feature files are located at:
```
src/test/resources/features/acceptance/
├── business-rules.feature      # Business rule validation scenarios
├── error-handling.feature      # Error handling scenarios
├── event-sourcing.feature      # Event sourcing verification
├── observability.feature       # Observability platform tests
├── product-lifecycle.feature   # Product CRUD and status transitions
└── product-queries.feature     # Query, search, and pagination
```

#### Writing New Acceptance Tests

1. Create or update a `.feature` file in `src/test/resources/features/acceptance/`
2. Write scenarios using Gherkin syntax (Given/When/Then)
3. Add step definitions in `src/test/kotlin/.../acceptance/steps/`
4. Tag scenarios appropriately for selective execution

Example feature file:
```gherkin
@product-lifecycle @smoke
Feature: Product Lifecycle Management
  As a product administrator
  I want to manage products through their lifecycle
  So that I can maintain an accurate product catalog

  Background:
    Given the system is running
    And the product catalog is empty

  @happy-path
  Scenario: Create a new product
    When I create a product with SKU "TEST-001", name "Test Product", and price 1999 cents
    Then the response status should be CREATED
    And the product should be created successfully
```

### Debugging

**Attach Debugger:**
1. Start application with debug enabled: `./gradlew bootRun --debug-jvm`
2. Attach debugger to `localhost:5005`

**IDE Configurations:**
- VSCode: See `.vscode/launch.json`
- IntelliJ: See `.run/Local.run.xml`

### Database Migrations

```bash
# Check migration status
./gradlew flywayInfo

# Run migrations
./gradlew flywayMigrate

# Clean and rebuild (WARNING: deletes all data)
./gradlew flywayClean flywayMigrate
```

## Troubleshooting

### Services Won't Start

```bash
# Check what's running
make ps

# Check logs
make logs

# Clean restart
make clean
make start
```

### Can't Connect to Database

```bash
# Verify PostgreSQL is healthy
docker exec cqrs-postgres pg_isready -U cqrs_user

# Check credentials
cat .env

# Reset database
./scripts/reset-service.sh postgres
```

### Can't Connect to Vault

```bash
# Check Vault status
curl http://localhost:8200/v1/sys/health

# Check Vault logs
make logs-vault

# Re-initialize Vault
./scripts/reset-service.sh vault
```

### Application Won't Start

```bash
# Check dependencies are running
make health

# View application logs
./gradlew bootRun

# Check Java version
java -version  # Should be 21+
```

### Slow Startup

```bash
# Measure startup time
./scripts/measure-startup.sh

# Monitor resources
./scripts/monitor-resources.sh

# Optimize Docker settings:
# - Increase Docker memory (4GB+ recommended)
# - Use SSD for Docker storage
# - Enable BuildKit: export DOCKER_BUILDKIT=1
```

## Performance Expectations

- **Infrastructure Startup:** < 60 seconds
- **Total Memory Usage:** < 2GB RAM
- **Application Startup:** < 30 seconds

Monitor with:
```bash
./scripts/monitor-resources.sh
./scripts/measure-startup.sh
```

## Contributing

1. Follow the coding standards in `CLAUDE.md` and `CONSTITUTION.md`
2. Write tests for all new features
3. Ensure migrations are reversible when possible
4. Update documentation for configuration changes

## Documentation

- **[CLAUDE.md](CLAUDE.md)** - Quick reference for AI-assisted development
- **[CONSTITUTION.md](documentation/CONSTITUTION.md)** - Complete coding standards
- **[VAULT_SETUP.md](VAULT_SETUP.md)** - Vault configuration guide
- **[Feature Plans](documentation/plans/)** - Implementation roadmaps
- **[Developer Portal](docs/README.md)** - Comprehensive developer guide

## License

This is a demonstration project for learning purposes.
