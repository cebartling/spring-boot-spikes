# Infrastructure Components

This document describes the infrastructure services that support the CQRS Spike application.

## Service Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     Docker Network (cqrs-network)            │
│                                                              │
│  ┌────────────────┐   ┌────────────────┐   ┌─────────────┐  │
│  │     Vault      │   │   PostgreSQL   │   │ Application │  │
│  │   (secrets)    │   │   (database)   │   │  (runtime)  │  │
│  │   :8200        │   │   :5432        │   │  :8080      │  │
│  └────────────────┘   └────────────────┘   └─────────────┘  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## HashiCorp Vault

### Purpose

Vault provides centralized secrets management:
- Database credentials
- API keys
- Encryption keys
- Service tokens

### Configuration

**Container:** `cqrs-vault`

**Image:** `hashicorp/vault:latest`

**Ports:**
| Port | Protocol | Purpose |
|------|----------|---------|
| 8200 | HTTP | API and UI |

**Environment Variables:**
| Variable | Value | Purpose |
|----------|-------|---------|
| `VAULT_DEV_ROOT_TOKEN_ID` | `dev-root-token` | Root authentication token |
| `VAULT_DEV_LISTEN_ADDRESS` | `0.0.0.0:8200` | Listen address |
| `VAULT_ADDR` | `http://0.0.0.0:8200` | Internal address |

### Health Check

```yaml
healthcheck:
  test: ["CMD", "sh", "-c", "VAULT_ADDR='http://127.0.0.1:8200' vault status"]
  interval: 5s
  timeout: 3s
  retries: 10
  start_period: 10s
```

### Volumes

| Host Path | Container Path | Purpose |
|-----------|----------------|---------|
| `./infrastructure/vault/data` | `/vault/data` | Persistent data |
| `./infrastructure/vault/scripts` | `/vault/scripts` | Initialization scripts |

### Initialization

The `vault-init` container runs after Vault is healthy to:
1. Enable KV secrets engine
2. Create application secrets
3. Set up secret paths

**Script:** `infrastructure/vault/scripts/init-secrets.sh`

### Access

**UI:** http://localhost:8200/ui
**Token:** `dev-root-token`

**CLI:**
```bash
export VAULT_ADDR='http://localhost:8200'
export VAULT_TOKEN='dev-root-token'
vault kv get secret/cqrs-spike/database
```

## PostgreSQL

### Purpose

PostgreSQL serves as the primary database:
- Event store (event sourcing)
- Read model (query projections)
- Command model (write side state)

### Configuration

**Container:** `cqrs-postgres`

**Image:** `postgres:18-alpine`

**Ports:**
| Port | Protocol | Purpose |
|------|----------|---------|
| 5432 | TCP | PostgreSQL connections |

**Environment Variables:**
| Variable | Default | Purpose |
|----------|---------|---------|
| `POSTGRES_DB` | `cqrs_db` | Database name |
| `POSTGRES_USER` | `cqrs_user` | Database user |
| `POSTGRES_PASSWORD` | `local_dev_password` | User password |

### Health Check

```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U cqrs_user -d cqrs_db"]
  interval: 5s
  timeout: 3s
  retries: 10
  start_period: 15s
```

### Volumes

| Volume | Container Path | Purpose |
|--------|----------------|---------|
| `postgres-data` | `/var/lib/postgresql/data` | Persistent database |

### Schemas

```
cqrs_db
├── event_store    # Event sourcing tables
├── read_model     # Query projections
└── command_model  # Command side state
```

### Connection Details

**From Host:**
```
Host: localhost
Port: 5432
Database: cqrs_db
Username: cqrs_user
Password: local_dev_password
```

**JDBC URL:**
```
jdbc:postgresql://localhost:5432/cqrs_db
```

**R2DBC URL:**
```
r2dbc:postgresql://localhost:5432/cqrs_db
```

**From Docker Network:**
```
Host: postgres
Port: 5432
```

### Access

```bash
# Makefile
make shell-postgres

# Docker
docker exec -it cqrs-postgres psql -U cqrs_user -d cqrs_db

# psql from host
psql -h localhost -p 5432 -U cqrs_user -d cqrs_db
```

## Application (Runtime)

### Purpose

The Spring Boot application runs the CQRS business logic.

### Configuration

**Ports:**
| Port | Protocol | Purpose |
|------|----------|---------|
| 8080 | HTTP | REST API |
| 5005 | TCP | Debug (when enabled) |

### Dependencies

The application depends on:
1. **Vault** - For secrets (database credentials)
2. **PostgreSQL** - For data persistence

### Health Endpoint

```
GET http://localhost:8080/actuator/health
```

Response:
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "vault": { "status": "UP" }
  }
}
```

### Running Options

**Gradle (Development):**
```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

**With Debug:**
```bash
./gradlew bootRun --debug-jvm
```

## Service Dependencies

```
┌─────────────────┐
│   Application   │
│   (Spring Boot) │
└────────┬────────┘
         │
         ├──────────────────┐
         │                  │
         ▼                  ▼
┌─────────────────┐  ┌─────────────────┐
│     Vault       │  │   PostgreSQL    │
│  (credentials)  │  │   (data store)  │
└─────────────────┘  └─────────────────┘
         │
         │ depends on
         ▼
┌─────────────────┐
│   vault-init    │
│ (initialization)│
└─────────────────┘
```

### Startup Order

1. **Vault** starts first (no dependencies)
2. **Vault-init** runs after Vault is healthy
3. **PostgreSQL** starts after Vault is healthy
4. **Application** starts after all services healthy

## Resource Allocation

### Default Limits

| Service | Memory | CPU |
|---------|--------|-----|
| Vault | 256MB | 0.5 |
| PostgreSQL | 512MB | 1.0 |
| Application | 512MB+ | 1.0+ |

### Tuning

Override in `docker-compose.override.yml`:

```yaml
services:
  postgres:
    deploy:
      resources:
        limits:
          memory: 1G
          cpus: '2.0'
```

## Logging

### Configuration

All services use JSON logging:

```yaml
logging:
  driver: "json-file"
  options:
    max-size: "10m"
    max-file: "3"
```

### Viewing Logs

```bash
# All services
make logs

# Specific service
make logs-vault
make logs-postgres

# Application
./scripts/logs.sh app
```

## Networking

### Docker Network

**Name:** `cqrs-network`
**Driver:** `bridge`
**Subnet:** `172.28.0.0/16`

### Service Discovery

Services reference each other by container name:
- `vault` for Vault
- `postgres` for PostgreSQL

### Port Mapping

| Service | Internal Port | External Port |
|---------|---------------|---------------|
| Vault | 8200 | 8200 |
| PostgreSQL | 5432 | 5432 |
| Application | 8080 | 8080 |

## Monitoring

### Resource Monitoring

```bash
./scripts/monitor-resources.sh
```

### Health Checks

```bash
make health
```

### Startup Timing

```bash
./scripts/measure-startup.sh
```

## See Also

- [Architecture Overview](overview.md) - System architecture
- [Networking](networking.md) - Network details
- [Security](security.md) - Security configuration
- [docker-compose.yml](../../docker-compose.yml) - Full configuration
