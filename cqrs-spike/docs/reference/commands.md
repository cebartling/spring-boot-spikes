# Command Reference

Complete reference of all available commands for the CQRS Spike project.

## Makefile Commands

### Infrastructure Management

| Command | Description | Example |
|---------|-------------|---------|
| `make start` | Start all infrastructure services | `make start` |
| `make stop` | Stop all services | `make stop` |
| `make restart` | Restart all services | `make restart` |
| `make clean` | Stop and remove volumes | `make clean` |
| `make ps` | Show service status | `make ps` |
| `make health` | Check service health | `make health` |
| `make init-env` | Create .env from template | `make init-env` |

### Application Build

| Command | Description | Example |
|---------|-------------|---------|
| `make build` | Build application (skip tests) | `make build` |
| `make test` | Run all tests | `make test` |

### Log Management

| Command | Description | Example |
|---------|-------------|---------|
| `make logs` | View all logs (follow) | `make logs` |
| `make logs-vault` | View Vault logs | `make logs-vault` |
| `make logs-postgres` | View PostgreSQL logs | `make logs-postgres` |
| `make logs-errors` | View error logs only | `make logs-errors` |
| `make logs-dashboard` | Open tmux log dashboard | `make logs-dashboard` |

### Database Operations

| Command | Description | Example |
|---------|-------------|---------|
| `make db-seed` | Seed with minimal data | `make db-seed` |
| `make db-seed-standard` | Seed with standard data | `make db-seed-standard` |
| `make db-seed-full` | Seed with full data | `make db-seed-full` |
| `make db-seed-perf` | Seed with performance data | `make db-seed-perf` |
| `make db-reset` | Reset database | `make db-reset` |
| `make db-reset-data` | Reset data only | `make db-reset-data` |
| `make db-scenario` | Load scenario interactively | `make db-scenario` |
| `make shell-postgres` | Open psql shell | `make shell-postgres` |
| `make db-query` | Run database query | `make db-query QUERY="SELECT 1"` |

### Vault Operations

| Command | Description | Example |
|---------|-------------|---------|
| `make shell-vault` | Open Vault shell | `make shell-vault` |
| `make vault-get` | Get Vault secret | `make vault-get PATH="secret/cqrs-spike/database"` |
| `make reset-vault` | Reset Vault service | `make reset-vault` |

### Monitoring

| Command | Description | Example |
|---------|-------------|---------|
| `make monitor` | Monitor resource usage | `make monitor` |
| `make resources` | Alias for monitor | `make resources` |
| `make perf` | Run performance tests | `make perf` |
| `make measure-startup` | Measure startup time | `make measure-startup` |

### Testing

| Command | Description | Example |
|---------|-------------|---------|
| `make test` | Run all tests | `make test` |
| `make test-docs` | Test documentation accuracy | `make test-docs` |

## Gradle Commands

### Build Commands

| Command | Description |
|---------|-------------|
| `./gradlew build` | Full build with tests |
| `./gradlew clean build` | Clean and build |
| `./gradlew build -x test` | Build without tests |
| `./gradlew clean` | Clean build artifacts |

### Test Commands

| Command | Description |
|---------|-------------|
| `./gradlew test` | Run all tests |
| `./gradlew test --tests "ClassName"` | Run specific test class |
| `./gradlew test --tests "ClassName.methodName"` | Run specific test method |
| `./gradlew test --info` | Run tests with verbose output |
| `./gradlew test jacocoTestReport` | Run tests with coverage |
| `./gradlew integrationTest` | Run integration tests |

### Application Commands

| Command | Description |
|---------|-------------|
| `./gradlew bootRun` | Run application |
| `./gradlew bootRun --args='--spring.profiles.active=local'` | Run with local profile |
| `./gradlew bootRun --debug-jvm` | Run with debug port 5005 |

### Database Migration Commands

| Command | Description |
|---------|-------------|
| `./gradlew flywayInfo` | Show migration status |
| `./gradlew flywayMigrate` | Run pending migrations |
| `./gradlew flywayValidate` | Validate migrations |
| `./gradlew flywayRepair` | Repair migration checksums |
| `./gradlew flywayClean` | Drop all objects (WARNING) |

### Code Quality Commands

| Command | Description |
|---------|-------------|
| `./gradlew ktlintCheck` | Check Kotlin code style |
| `./gradlew ktlintFormat` | Auto-format Kotlin code |
| `./gradlew check` | Run all checks |
| `./gradlew dependencyCheckAnalyze` | Check dependencies for vulnerabilities |

## Docker Commands

### Container Management

| Command | Description |
|---------|-------------|
| `docker compose up -d` | Start all services |
| `docker compose up -d vault` | Start specific service |
| `docker compose down` | Stop all services |
| `docker compose down -v` | Stop and remove volumes |
| `docker compose restart` | Restart all services |
| `docker compose restart postgres` | Restart specific service |
| `docker compose ps` | List running containers |

### Log Commands

| Command | Description |
|---------|-------------|
| `docker compose logs` | View all logs |
| `docker compose logs -f` | Follow all logs |
| `docker compose logs -f vault` | Follow specific service logs |
| `docker compose logs --tail 100` | Last 100 lines |

### Container Shell Access

| Command | Description |
|---------|-------------|
| `docker exec -it cqrs-vault sh` | Shell into Vault |
| `docker exec -it cqrs-postgres sh` | Shell into PostgreSQL |
| `docker exec -it cqrs-postgres psql -U cqrs_user -d cqrs_db` | psql shell |

### Image Management

| Command | Description |
|---------|-------------|
| `docker compose pull` | Pull latest images |
| `docker compose build` | Build images |
| `docker compose build --no-cache` | Rebuild without cache |
| `docker image prune` | Remove unused images |
| `docker system prune` | Clean up everything |

### Network Commands

| Command | Description |
|---------|-------------|
| `docker network ls` | List networks |
| `docker network inspect cqrs-network` | Inspect network |
| `docker network rm cqrs-network` | Remove network |

## Script Commands

### Infrastructure Scripts

| Script | Description | Usage |
|--------|-------------|-------|
| `start-infrastructure.sh` | Start all services | `./scripts/start-infrastructure.sh` |
| `stop-infrastructure.sh` | Stop all services | `./scripts/stop-infrastructure.sh` |
| `stop-infrastructure.sh --clean` | Stop and clean | `./scripts/stop-infrastructure.sh --clean` |
| `health-check.sh` | Check service health | `./scripts/health-check.sh` |
| `reset-service.sh` | Reset a service | `./scripts/reset-service.sh vault` |

### Log Scripts

| Script | Description | Usage |
|--------|-------------|-------|
| `logs.sh app` | Application logs | `./scripts/logs.sh app` |
| `logs.sh errors` | Error logs only | `./scripts/logs.sh errors` |
| `logs.sh warnings` | Warning logs only | `./scripts/logs.sh warnings` |
| `logs.sh tail` | Last 100 lines | `./scripts/logs.sh tail` |
| `logs-dashboard.sh` | Tmux log dashboard | `./scripts/logs-dashboard.sh` |

### Database Scripts

| Script | Description | Usage |
|--------|-------------|-------|
| `db-query.sh` | Run SQL query | `./scripts/db-query.sh "SELECT 1"` |
| `seed.sh` | Seed database | `./infrastructure/postgres/seed-data/scripts/seed.sh minimal` |
| `reset.sh` | Reset database | `./infrastructure/postgres/seed-data/scripts/reset.sh` |
| `reset-data-only.sh` | Reset data only | `./infrastructure/postgres/seed-data/scripts/reset-data-only.sh` |

### Vault Scripts

| Script | Description | Usage |
|--------|-------------|-------|
| `vault-get.sh` | Get secret | `./scripts/vault-get.sh secret/cqrs-spike/database` |
| `vault-get.sh list` | List secrets | `./scripts/vault-get.sh list` |

### Monitoring Scripts

| Script | Description | Usage |
|--------|-------------|-------|
| `monitor-resources.sh` | Monitor resource usage | `./scripts/monitor-resources.sh` |
| `measure-startup.sh` | Measure startup time | `./scripts/measure-startup.sh` |
| `test-performance.sh` | Run performance tests | `./scripts/test-performance.sh` |
| `test-documentation.sh` | Verify documentation | `./scripts/test-documentation.sh` |

### Utility Scripts

| Script | Description | Usage |
|--------|-------------|-------|
| `create-migration.sh` | Create new migration | `./scripts/create-migration.sh "add_feature"` |
| `view-logs.sh` | View logs with options | `./scripts/view-logs.sh` |

## Vault CLI Commands

Set environment first:
```bash
export VAULT_ADDR='http://localhost:8200'
export VAULT_TOKEN='dev-root-token'
```

| Command | Description |
|---------|-------------|
| `vault status` | Check Vault status |
| `vault kv get secret/cqrs-spike/database` | Get secret |
| `vault kv get -field=password secret/cqrs-spike/database` | Get specific field |
| `vault kv put secret/cqrs-spike/custom key=value` | Create secret |
| `vault kv patch secret/cqrs-spike/database key=value` | Update field |
| `vault kv list secret/cqrs-spike` | List secrets |
| `vault kv delete secret/cqrs-spike/custom` | Delete secret |

## PostgreSQL Commands

### psql Commands

| Command | Description |
|---------|-------------|
| `\l` | List databases |
| `\c cqrs_db` | Connect to database |
| `\dn` | List schemas |
| `\dt` | List tables |
| `\dt event_store.*` | List tables in schema |
| `\d+ table_name` | Describe table |
| `\di` | List indexes |
| `\q` | Quit |

### Connection Command

```bash
psql -h localhost -p 5432 -U cqrs_user -d cqrs_db
```

## Quick Reference

### Daily Workflow

```bash
make start          # Start infrastructure
make health         # Verify health
./gradlew bootRun --args='--spring.profiles.active=local'  # Run app
make test           # Run tests
make stop           # Stop when done
```

### Reset Everything

```bash
make clean
make start
./gradlew flywayMigrate
make db-seed
```

### Debug Mode

```bash
./gradlew bootRun --debug-jvm
# Attach debugger to localhost:5005
```

## See Also

- [Environment Variables](environment-variables.md)
- [Ports and URLs](ports-and-urls.md)
- [Scripts Reference](scripts.md)
