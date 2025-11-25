# CQRS Spike - Developer Portal

Welcome to the CQRS Spike developer documentation. This guide provides comprehensive information for developing, testing, and operating the CQRS application.

## Table of Contents

- [Quick Start](#quick-start)
- [Common Tasks](#common-tasks)
- [Development Workflow](#development-workflow)
- [Service URLs](#service-urls)
- [Helper Scripts](#helper-scripts)
- [Troubleshooting](#troubleshooting)
- [Architecture](#architecture)
- [Testing](#testing)

## Quick Start

### Prerequisites

- Docker Desktop (version 20.10+)
- Java 21
- Gradle 8+
- Optional: tmux (for log dashboard)

### First Time Setup

1. **Clone and navigate to the project:**
   ```bash
   cd cqrs-spike
   ```

2. **Initialize environment:**
   ```bash
   make init-env
   ```

3. **Start infrastructure:**
   ```bash
   make start
   ```

4. **Verify services are healthy:**
   ```bash
   make health
   ```

5. **Build the application:**
   ```bash
   make build
   ```

6. **Run the application:**
   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=local'
   ```

### Daily Development Workflow

```bash
# Start infrastructure
make start

# Run application (with hot reload)
./gradlew bootRun --args='--spring.profiles.active=local'

# In another terminal, watch logs
./scripts/logs.sh app

# Make code changes...

# Run tests
make test

# Stop infrastructure when done
make stop
```

## Common Tasks

### Infrastructure Management

| Command | Description |
|---------|-------------|
| `make start` | Start all infrastructure services |
| `make stop` | Stop all services |
| `make restart` | Restart everything |
| `make clean` | Stop and remove volumes (fresh start) |
| `make health` | Check service health |
| `make ps` | Show service status |

### Application Development

| Command | Description |
|---------|-------------|
| `make build` | Build application (skip tests) |
| `make test` | Run all tests |
| `./gradlew bootRun` | Run application |
| `./gradlew bootRun --debug-jvm` | Run with debug port 5005 |
| `./gradlew clean build` | Clean and rebuild |

### Viewing Logs

| Command | Description |
|---------|-------------|
| `make logs` | View all logs (follow mode) |
| `make logs-vault` | View Vault logs |
| `make logs-postgres` | View PostgreSQL logs |
| `./scripts/logs.sh app` | View application logs |
| `./scripts/logs.sh errors` | Filter errors only |
| `./scripts/logs.sh warnings` | Filter warnings only |
| `./scripts/logs.sh tail` | Last 100 lines |
| `./scripts/logs-dashboard.sh` | Multi-pane log dashboard (requires tmux) |

### Database Operations

| Command | Description |
|---------|-------------|
| `make shell-postgres` | Open psql shell |
| `./scripts/db-query.sh "SELECT ..."` | Run a query |
| `./scripts/db-query.sh "\\dt event_store.*"` | List event tables |
| `./scripts/db-query.sh "\\dn"` | List schemas |
| `./gradlew flywayInfo` | Check migration status |
| `./gradlew flywayMigrate` | Run migrations |

### Vault Operations

| Command | Description |
|---------|-------------|
| `make shell-vault` | Open Vault shell |
| `./scripts/vault-get.sh secret/cqrs-spike/database` | Get database secrets |
| `./scripts/vault-get.sh list` | List all secrets |

### Service Management

| Command | Description |
|---------|-------------|
| `./scripts/reset-service.sh vault` | Reset Vault |
| `./scripts/reset-service.sh postgres` | Reset database (deletes data!) |
| `./scripts/reset-service.sh all` | Restart all services |

## Development Workflow

### Making Code Changes

1. **Start infrastructure:**
   ```bash
   make start
   ```

2. **Run application with local profile:**
   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=local'
   ```

3. **Make changes to code** - Spring DevTools will auto-reload

4. **Run tests:**
   ```bash
   make test
   ```

5. **Check code quality:**
   ```bash
   ./gradlew ktlintCheck
   ```

### Running with IDE

#### VSCode

1. Open project in VSCode
2. Use Run/Debug configuration: "Run CQRS App (Local)"
3. Set breakpoints
4. Press F5

#### IntelliJ IDEA

1. Open project in IntelliJ
2. Use run configuration: "CQRS Application (Local)"
3. Click Run or Debug button

### Debugging

**Remote Debugging:**
```bash
# Start with debug port
./gradlew bootRun --debug-jvm

# Attach debugger to localhost:5005
```

**Docker Debugging:**
```bash
# Set DEBUG env var in docker-compose.yml
DEBUG=true docker-compose up app
```

## Service URLs

### Development URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| Vault UI | http://localhost:8200/ui | Token: `dev-root-token` |
| Vault API | http://localhost:8200 | Token: `dev-root-token` |
| PostgreSQL | localhost:5432 | User: `cqrs_user`, Password: `local_dev_password` |
| Application | http://localhost:8080 | - |
| Health Endpoint | http://localhost:8080/actuator/health | - |
| Debug Port | localhost:5005 | JDWP |

### Internal Docker URLs

| Service | Internal URL |
|---------|--------------|
| Vault | http://vault:8200 |
| PostgreSQL | postgres:5432 |

## Helper Scripts

All helper scripts are located in the `scripts/` directory:

### Log Management
- **logs.sh** - View and filter logs
- **logs-dashboard.sh** - Multi-pane tmux dashboard

### Database Tools
- **db-query.sh** - Execute SQL queries
- **reset-service.sh** - Reset services

### Vault Tools
- **vault-get.sh** - Retrieve secrets

### Monitoring
- **monitor-resources.sh** - Check resource usage
- **measure-startup.sh** - Measure startup time
- **health-check.sh** - Verify service health

### Infrastructure
- **start-infrastructure.sh** - Start all services
- **stop-infrastructure.sh** - Stop all services

### Testing
- **test-documentation.sh** - Verify connection details
- **test-performance.sh** - Test performance metrics

## Troubleshooting

### Services Won't Start

```bash
# Check Docker is running
docker info

# Check what's running
make ps

# View logs
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

# Check connection
./scripts/db-query.sh "SELECT 1"

# Reset if needed
./scripts/reset-service.sh postgres
```

### Can't Connect to Vault

```bash
# Check Vault status
curl http://localhost:8200/v1/sys/health

# Check Vault logs
make logs-vault

# Re-initialize
./scripts/reset-service.sh vault
```

### Application Won't Start

```bash
# Check Java version
java -version  # Should be 21+

# Check infrastructure
make health

# Clean build
./gradlew clean build

# Check for port conflicts
lsof -i :8080
```

### Slow Performance

```bash
# Check resource usage
./scripts/monitor-resources.sh

# Measure startup time
./scripts/measure-startup.sh

# Increase Docker resources (Preferences > Resources)
# - Memory: 4GB+
# - CPUs: 2+
# - Swap: 1GB+
```

### Tests Failing

```bash
# Run with verbose output
./gradlew test --info

# Run specific test
./gradlew test --tests "ProductServiceTest"

# Clean and rebuild
./gradlew clean test
```

## Architecture

### Schema Organization

- **event_store** - Event sourcing events
  - `event_stream` - Stream metadata
  - `domain_event` - Event data (JSONB)

- **read_model** - Query/read side
  - Denormalized views
  - Optimized for queries

- **command_model** - Command/write side
  - Current state
  - Optimized for writes

### Technology Stack

- **Kotlin 2.2.21** - Primary language
- **Spring Boot 4.0** - Application framework
- **Spring WebFlux** - Reactive web framework
- **R2DBC** - Reactive database access
- **PostgreSQL 18** - Database
- **Vault** - Secrets management
- **Flyway** - Database migrations
- **Resilience4j** - Circuit breakers, retries

### Patterns

- **CQRS** - Command Query Responsibility Segregation
- **Event Sourcing** - Immutable event log
- **Reactive Streams** - Non-blocking I/O
- **Repository Pattern** - Data access abstraction

## Testing

### Unit Tests

```bash
# Run all unit tests
./gradlew test

# Run specific class
./gradlew test --tests "ProductServiceTest"

# With coverage
./gradlew test jacocoTestReport
```

### Integration Tests

```bash
# Start infrastructure first
make start

# Run integration tests
./gradlew integrationTest
```

### Testing Patterns

```kotlin
// Service test
@ExtendWith(MockitoExtension::class)
class ProductServiceTest {
    @Mock private lateinit var repo: ProductRepository
    @InjectMocks private lateinit var service: ProductService

    @Test
    fun `should find product`() {
        // StepVerifier for reactive tests
    }
}

// Controller test
@WebFluxTest(ProductController::class)
class ProductControllerTest {
    @Autowired private lateinit var client: WebTestClient
    // Test reactive endpoints
}
```

## Performance Targets

- **Infrastructure Startup:** < 60 seconds
- **Application Startup:** < 30 seconds
- **Total Memory Usage:** < 2GB RAM
- **Test Suite:** < 60 seconds

Monitor with:
```bash
./scripts/monitor-resources.sh
./scripts/measure-startup.sh
```

## Additional Resources

- [Main README](../README.md) - Project overview
- [CLAUDE.md](../CLAUDE.md) - AI development guide
- [CONSTITUTION.md](../documentation/CONSTITUTION.md) - Coding standards
- [VAULT_SETUP.md](../VAULT_SETUP.md) - Vault configuration
- [Feature Plans](../documentation/plans/) - Implementation roadmaps

## Getting Help

- Check this documentation first
- Review troubleshooting section
- Check application logs: `./scripts/logs.sh app`
- Verify infrastructure: `make health`
- Ask the team in #cqrs-spike channel
