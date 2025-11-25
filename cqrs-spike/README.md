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
| Application | http://localhost:8080 | N/A |
| Health Check | http://localhost:8080/actuator/health | N/A |
| Debug Port | localhost:5005 | JDWP |

### Internal Service URLs (Docker Network)

| Service | Internal URL | Used By |
|---------|--------------|---------|
| Vault | http://vault:8200 | Application |
| PostgreSQL | postgres:5432 | Application |

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

## Common Tasks

### Infrastructure Management

- `make start` - Start all infrastructure services (Vault, PostgreSQL)
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
- **Observability:** OpenTelemetry
- **Testing:** JUnit 5, Mockito, StepVerifier

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

```bash
# All tests
make test

# Specific test
./gradlew test --tests "ProductServiceTest"

# With coverage
./gradlew test jacocoTestReport
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
