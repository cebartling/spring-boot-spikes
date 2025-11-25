# Scripts Reference

Complete reference of helper scripts in the CQRS Spike project.

## Script Overview

All scripts are located in the `scripts/` directory (with some in `infrastructure/`).

```
scripts/
├── start-infrastructure.sh     # Start all services
├── stop-infrastructure.sh      # Stop services
├── health-check.sh            # Check service health
├── reset-service.sh           # Reset individual services
├── logs.sh                    # View and filter logs
├── logs-dashboard.sh          # Multi-pane log view
├── db-query.sh                # Run database queries
├── vault-get.sh               # Retrieve Vault secrets
├── monitor-resources.sh       # Monitor resource usage
├── measure-startup.sh         # Measure startup time
├── test-documentation.sh      # Verify documentation
├── test-performance.sh        # Performance testing
├── test-seeding.sh            # Test seed data
├── create-migration.sh        # Create new migration
└── view-logs.sh               # Alternative log viewer
```

## Infrastructure Scripts

### start-infrastructure.sh

Starts all infrastructure services with health checking.

**Usage:**
```bash
./scripts/start-infrastructure.sh
```

**What it does:**
1. Starts Docker Compose services
2. Waits for Vault to be healthy
3. Runs Vault initialization
4. Waits for PostgreSQL to be healthy
5. Reports status

**Options:**
```bash
./scripts/start-infrastructure.sh --verbose   # Verbose output
./scripts/start-infrastructure.sh --quick     # Skip health waits
```

### stop-infrastructure.sh

Stops all infrastructure services.

**Usage:**
```bash
./scripts/stop-infrastructure.sh           # Stop services
./scripts/stop-infrastructure.sh --clean   # Stop and remove volumes
```

**Options:**
| Option | Description |
|--------|-------------|
| `--clean` | Remove volumes (data loss) |
| `--force` | Force stop |

### health-check.sh

Checks health of all services.

**Usage:**
```bash
./scripts/health-check.sh
```

**Output:**
```
Checking service health...
[OK] Vault is healthy
[OK] PostgreSQL is healthy
All services are healthy!
```

**Exit Codes:**
| Code | Meaning |
|------|---------|
| 0 | All healthy |
| 1 | One or more unhealthy |

### reset-service.sh

Resets individual services.

**Usage:**
```bash
./scripts/reset-service.sh vault      # Reset Vault
./scripts/reset-service.sh postgres   # Reset PostgreSQL
./scripts/reset-service.sh all        # Reset all services
```

**What it does:**
- Stops the service
- Removes its volume (if applicable)
- Restarts the service
- Waits for health

## Log Management Scripts

### logs.sh

View and filter application logs.

**Usage:**
```bash
./scripts/logs.sh                 # All logs
./scripts/logs.sh app             # Application logs only
./scripts/logs.sh vault           # Vault logs only
./scripts/logs.sh postgres        # PostgreSQL logs only
./scripts/logs.sh errors          # Error logs only
./scripts/logs.sh warnings        # Warning logs only
./scripts/logs.sh tail            # Last 100 lines
```

**Options:**
| Argument | Description |
|----------|-------------|
| `app` | Application container logs |
| `vault` | Vault container logs |
| `postgres` | PostgreSQL container logs |
| `errors` | Filter for ERROR level |
| `warnings` | Filter for WARN level |
| `tail` | Last 100 lines |
| `-f` | Follow mode |

### logs-dashboard.sh

Opens a tmux dashboard showing multiple log streams.

**Usage:**
```bash
./scripts/logs-dashboard.sh
```

**Requirements:**
- tmux installed
- Running terminal

**Layout:**
```
┌─────────────────┬─────────────────┐
│  Application    │  Vault          │
│  Logs           │  Logs           │
├─────────────────┴─────────────────┤
│           PostgreSQL Logs         │
└───────────────────────────────────┘
```

**Controls:**
- `Ctrl+B` then arrow keys: Switch panes
- `Ctrl+B` then `d`: Detach
- `exit` or `Ctrl+D`: Close pane

### view-logs.sh

Alternative log viewer with more options.

**Usage:**
```bash
./scripts/view-logs.sh [service] [options]
```

## Database Scripts

### db-query.sh

Run SQL queries against the database.

**Usage:**
```bash
./scripts/db-query.sh "SELECT 1"
./scripts/db-query.sh "SELECT * FROM event_store.domain_event LIMIT 5"
./scripts/db-query.sh "\dt event_store.*"
```

**Examples:**
```bash
# Count events
./scripts/db-query.sh "SELECT COUNT(*) FROM event_store.domain_event"

# List schemas
./scripts/db-query.sh "\dn"

# Describe table
./scripts/db-query.sh "\d event_store.domain_event"
```

### create-migration.sh

Create a new Flyway migration file.

**Usage:**
```bash
./scripts/create-migration.sh "description_of_change"
```

**Example:**
```bash
./scripts/create-migration.sh "add_audit_columns"
# Creates: src/main/resources/db/migration/V004__add_audit_columns.sql
```

**Output:**
- Creates numbered migration file
- Opens file in default editor (if EDITOR set)

## Vault Scripts

### vault-get.sh

Retrieve secrets from Vault.

**Usage:**
```bash
./scripts/vault-get.sh secret/cqrs-spike/database     # Get database secrets
./scripts/vault-get.sh secret/cqrs-spike/api-keys     # Get API keys
./scripts/vault-get.sh list                           # List all secrets
```

**Examples:**
```bash
# Get all database credentials
./scripts/vault-get.sh secret/cqrs-spike/database

# Output:
# Key          Value
# ---          -----
# password     local_dev_password
# url          jdbc:postgresql://postgres:5432/cqrs_db
# username     cqrs_user
```

**Options:**
| Argument | Description |
|----------|-------------|
| `list` | List available secret paths |
| `<path>` | Full path to secret |

## Monitoring Scripts

### monitor-resources.sh

Monitor Docker container resource usage.

**Usage:**
```bash
./scripts/monitor-resources.sh
```

**Output:**
```
Container          CPU %     MEM USAGE / LIMIT     NET I/O
cqrs-vault         0.12%     45MiB / 256MiB        1.5kB / 2.3kB
cqrs-postgres      0.05%     120MiB / 512MiB       3.2kB / 4.1kB
```

**What it monitors:**
- CPU usage
- Memory usage and limits
- Network I/O
- Block I/O

### measure-startup.sh

Measure infrastructure startup time.

**Usage:**
```bash
./scripts/measure-startup.sh
```

**Output:**
```
Starting infrastructure...
Vault ready:      12.3s
PostgreSQL ready: 18.7s
Total startup:    23.5s
```

**What it measures:**
- Time for each service to become healthy
- Total infrastructure startup time

## Testing Scripts

### test-documentation.sh

Verify documentation accuracy.

**Usage:**
```bash
./scripts/test-documentation.sh
```

**What it tests:**
- Service URLs are accessible
- Database connections work
- Vault secrets exist
- Health endpoints respond

### test-performance.sh

Run performance tests.

**Usage:**
```bash
./scripts/test-performance.sh
```

**What it tests:**
- Query response times
- Event store write performance
- Connection pool behavior

### test-seeding.sh

Test database seeding functionality.

**Usage:**
```bash
./scripts/test-seeding.sh
```

**What it tests:**
- Seed data loads correctly
- Data integrity
- Scenario switching

## Database Seeding Scripts

Located in `infrastructure/postgres/seed-data/scripts/`:

### seed.sh

Load seed data into database.

**Usage:**
```bash
./infrastructure/postgres/seed-data/scripts/seed.sh minimal
./infrastructure/postgres/seed-data/scripts/seed.sh standard
./infrastructure/postgres/seed-data/scripts/seed.sh full
./infrastructure/postgres/seed-data/scripts/seed.sh performance
```

**Scenarios:**
| Scenario | Description |
|----------|-------------|
| `minimal` | Basic test data |
| `standard` | Realistic data set |
| `full` | Comprehensive data |
| `performance` | Large volume data |

### reset.sh

Reset database completely.

**Usage:**
```bash
./infrastructure/postgres/seed-data/scripts/reset.sh
```

**Warning:** This deletes all data and recreates schemas.

### reset-data-only.sh

Reset data but preserve schema.

**Usage:**
```bash
./infrastructure/postgres/seed-data/scripts/reset-data-only.sh
```

**What it does:**
- Truncates all tables
- Resets sequences
- Preserves schema structure

### load-scenario.sh

Interactive scenario loader.

**Usage:**
```bash
./infrastructure/postgres/seed-data/scripts/load-scenario.sh
```

**Presents menu:**
```
Select scenario:
1. minimal
2. standard
3. full
4. performance
Choice:
```

## Script Best Practices

### Making Scripts Executable

```bash
chmod +x scripts/*.sh
chmod +x infrastructure/postgres/seed-data/scripts/*.sh
```

### Error Handling

All scripts should:
- Exit on error: `set -e`
- Handle interrupts
- Provide meaningful exit codes

### Environment Variables

Scripts respect these environment variables:
- `VERBOSE`: Enable verbose output
- `QUIET`: Suppress output
- `DRY_RUN`: Show what would be done

## Troubleshooting Scripts

### Script Not Found

```bash
# Ensure you're in project root
cd /path/to/cqrs-spike

# Check script exists
ls -la scripts/

# Check executable
chmod +x scripts/start-infrastructure.sh
```

### Permission Denied

```bash
# Fix permissions
chmod +x scripts/*.sh
```

### Wrong Shell

Scripts require bash. If default shell is different:
```bash
bash ./scripts/start-infrastructure.sh
```

## See Also

- [Commands Reference](commands.md)
- [Daily Workflow](../guides/daily-workflow.md)
- [Troubleshooting](../troubleshooting/common-issues.md)
