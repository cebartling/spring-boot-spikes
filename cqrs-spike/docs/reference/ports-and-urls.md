# Ports and URLs Reference

Complete reference of all service ports and URLs for the CQRS Spike project.

## Service URLs Overview

| Service | URL | Purpose |
|---------|-----|---------|
| Application | http://localhost:8080 | REST API |
| Vault UI | http://localhost:8200/ui | Secrets management UI |
| Vault API | http://localhost:8200 | Vault REST API |
| PostgreSQL | localhost:5432 | Database |
| Debug | localhost:5005 | Java remote debugging |

## Port Assignments

### External Ports (Host Machine)

| Port | Protocol | Service | Description |
|------|----------|---------|-------------|
| 5005 | TCP | Application | JDWP debug port |
| 5432 | TCP | PostgreSQL | Database connections |
| 8080 | HTTP | Application | REST API |
| 8200 | HTTP | Vault | API and UI |

### Internal Ports (Docker Network)

Services communicate within Docker using container names:

| Service | Hostname | Port | URL |
|---------|----------|------|-----|
| Vault | `vault` | 8200 | `http://vault:8200` |
| PostgreSQL | `postgres` | 5432 | `postgres:5432` |
| Application | `app` | 8080 | `http://app:8080` |

## Application Endpoints

### Actuator Endpoints

| Endpoint | URL | Description |
|----------|-----|-------------|
| Health | http://localhost:8080/actuator/health | Application health status |
| Info | http://localhost:8080/actuator/info | Application information |
| Metrics | http://localhost:8080/actuator/metrics | Application metrics |
| Env | http://localhost:8080/actuator/env | Environment properties |

### API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/products` | GET | List products |
| `/api/products/{id}` | GET | Get product |
| `/api/products` | POST | Create product |
| `/api/products/{id}` | PUT | Update product |
| `/api/products/{id}` | DELETE | Delete product |

## Vault URLs

### Web Interface

| URL | Description |
|-----|-------------|
| http://localhost:8200/ui | Vault Web UI |
| http://localhost:8200/ui/vault/secrets/secret/list | Secret browser |

### API Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /v1/sys/health` | Health check |
| `GET /v1/sys/seal-status` | Seal status |
| `GET /v1/secret/data/cqrs-spike/database` | Database secrets |
| `GET /v1/secret/data/cqrs-spike/api-keys` | API key secrets |

### Authentication

```bash
# UI: Use token
Token: dev-root-token

# API: Header
X-Vault-Token: dev-root-token
```

## PostgreSQL Connections

### Connection Strings

**JDBC:**
```
jdbc:postgresql://localhost:5432/cqrs_db
```

**R2DBC:**
```
r2dbc:postgresql://localhost:5432/cqrs_db
```

**psql:**
```bash
psql -h localhost -p 5432 -U cqrs_user -d cqrs_db
```

### Connection Parameters

| Parameter | Value |
|-----------|-------|
| Host | `localhost` (from host) or `postgres` (from Docker) |
| Port | `5432` |
| Database | `cqrs_db` |
| Username | `cqrs_user` |
| Password | `local_dev_password` |
| SSL | `disable` (development) |

## Docker Network

### Network Configuration

| Property | Value |
|----------|-------|
| Network Name | `cqrs-network` |
| Driver | `bridge` |
| Subnet | `172.28.0.0/16` |

### Service Discovery

Within the Docker network, services use container names:

```
http://vault:8200      # Vault from app container
postgres:5432          # PostgreSQL from app container
```

## Port Configuration

### Changing Ports

To change default ports, edit `.env`:

```bash
# .env
APP_PORT=8081
POSTGRES_PORT=5433
VAULT_PORT=8201
DEBUG_PORT=5006
```

Then update `docker-compose.override.yml`:

```yaml
services:
  vault:
    ports:
      - "${VAULT_PORT:-8200}:8200"
  postgres:
    ports:
      - "${POSTGRES_PORT:-5432}:5432"
```

### Port Conflict Resolution

1. **Find process using port:**
   ```bash
   lsof -i :8080
   ```

2. **Kill process:**
   ```bash
   kill -9 <PID>
   ```

3. **Or use alternative port:**
   ```bash
   APP_PORT=8081 docker compose up -d
   ```

## Health Check URLs

### Quick Health Check

```bash
# Vault
curl http://localhost:8200/v1/sys/health

# PostgreSQL
docker exec cqrs-postgres pg_isready -U cqrs_user

# Application
curl http://localhost:8080/actuator/health
```

### Detailed Health

```bash
# Vault detailed
curl -H "X-Vault-Token: dev-root-token" http://localhost:8200/v1/sys/health

# Application detailed
curl http://localhost:8080/actuator/health | jq
```

## Testing Connectivity

### From Host

```bash
# Test HTTP endpoints
curl http://localhost:8080/actuator/health
curl http://localhost:8200/v1/sys/health

# Test TCP ports
nc -zv localhost 5432
nc -zv localhost 8200
nc -zv localhost 8080
```

### From Container

```bash
# Test from app container
docker exec cqrs-app curl http://vault:8200/v1/sys/health
docker exec cqrs-app nc -zv postgres 5432
```

## URL Quick Reference Card

### Development URLs

```
Application:     http://localhost:8080
Health:          http://localhost:8080/actuator/health
Vault UI:        http://localhost:8200/ui
Vault Health:    http://localhost:8200/v1/sys/health
PostgreSQL:      localhost:5432
Debug Port:      localhost:5005
```

### Credentials

```
Vault Token:     dev-root-token
PostgreSQL User: cqrs_user
PostgreSQL Pass: local_dev_password
PostgreSQL DB:   cqrs_db
```

### Docker Internal URLs

```
Vault:      http://vault:8200
PostgreSQL: postgres:5432
```

## API Examples

### Health Check

```bash
curl http://localhost:8080/actuator/health
```

Response:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "vault": {"status": "UP"}
  }
}
```

### Vault Secret

```bash
curl -H "X-Vault-Token: dev-root-token" \
     http://localhost:8200/v1/secret/data/cqrs-spike/database
```

### Database Query

```bash
psql -h localhost -p 5432 -U cqrs_user -d cqrs_db \
     -c "SELECT COUNT(*) FROM event_store.domain_event"
```

## See Also

- [Commands Reference](commands.md)
- [Environment Variables](environment-variables.md)
- [Networking Architecture](../architecture/networking.md)
- [Infrastructure Components](../architecture/infrastructure-components.md)
