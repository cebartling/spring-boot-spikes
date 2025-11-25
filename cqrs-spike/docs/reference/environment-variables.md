# Environment Variables Reference

Complete reference of environment variables used in the CQRS Spike project.

## Configuration Overview

Environment variables are defined in the `.env` file and used by Docker Compose and the application.

```bash
# Create from template
make init-env
# or
cp .env.example .env
```

## Vault Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `VAULT_ROOT_TOKEN` | `dev-root-token` | Root token for Vault authentication |
| `VAULT_ADDR` | `http://localhost:8200` | Vault server address |
| `VAULT_DEV_ROOT_TOKEN_ID` | `dev-root-token` | Dev mode root token |
| `VAULT_DEV_LISTEN_ADDRESS` | `0.0.0.0:8200` | Dev mode listen address |

### Usage

```yaml
# docker-compose.yml
environment:
  VAULT_DEV_ROOT_TOKEN_ID: ${VAULT_ROOT_TOKEN:-dev-root-token}
```

```kotlin
// Application
@Value("\${spring.cloud.vault.token}")
private lateinit var vaultToken: String
```

## PostgreSQL Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_DB` | `cqrs_db` | Database name |
| `POSTGRES_USER` | `cqrs_user` | Database user |
| `POSTGRES_PASSWORD` | `local_dev_password` | Database password |
| `POSTGRES_PORT` | `5432` | PostgreSQL port |

### Usage

```yaml
# docker-compose.yml
environment:
  POSTGRES_DB: ${POSTGRES_DB:-cqrs_db}
  POSTGRES_USER: ${POSTGRES_USER:-cqrs_user}
  POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-local_dev_password}
```

```bash
# Connection
psql -h localhost -p ${POSTGRES_PORT} -U ${POSTGRES_USER} -d ${POSTGRES_DB}
```

## Application Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | `local` | Active Spring profile |
| `APP_PORT` | `8080` | Application HTTP port |
| `DEBUG_PORT` | `5005` | Java debug port |
| `JAVA_OPTS` | `-Xms256m -Xmx512m` | JVM options |

### Usage

```bash
# Run with specific profile
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun

# Run with custom JVM options
JAVA_OPTS="-Xms512m -Xmx1g" ./gradlew bootRun
```

## Docker Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `COMPOSE_PROJECT_NAME` | `cqrs-spike` | Docker Compose project name |
| `DOCKER_BUILDKIT` | `1` | Enable BuildKit |

### Usage

```bash
# Set in environment
export DOCKER_BUILDKIT=1
docker compose build
```

## Spring Configuration

### Database Connection

| Property | Environment Variable | Default |
|----------|---------------------|---------|
| `spring.r2dbc.url` | `SPRING_R2DBC_URL` | `r2dbc:postgresql://localhost:5432/cqrs_db` |
| `spring.r2dbc.username` | `SPRING_R2DBC_USERNAME` | `cqrs_user` |
| `spring.r2dbc.password` | `SPRING_R2DBC_PASSWORD` | From Vault |

### Vault Connection

| Property | Environment Variable | Default |
|----------|---------------------|---------|
| `spring.cloud.vault.uri` | `SPRING_CLOUD_VAULT_URI` | `http://localhost:8200` |
| `spring.cloud.vault.token` | `VAULT_TOKEN` | `dev-root-token` |

### Logging

| Property | Environment Variable | Default |
|----------|---------------------|---------|
| `logging.level.root` | `LOGGING_LEVEL_ROOT` | `INFO` |
| `logging.level.com.example.cqrs` | `LOGGING_LEVEL_COM_EXAMPLE_CQRS` | `DEBUG` |

## Profile-Specific Variables

### Local Development (`application-local.yml`)

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/cqrs_db
  cloud:
    vault:
      uri: http://localhost:8200
      token: dev-root-token

logging:
  level:
    com.example.cqrs: DEBUG
```

### Docker Environment

When running in Docker, services communicate using internal hostnames:

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://postgres:5432/cqrs_db
  cloud:
    vault:
      uri: http://vault:8200
```

## .env File Template

```bash
# .env.example

# Vault Configuration
VAULT_ROOT_TOKEN=dev-root-token
VAULT_ADDR=http://localhost:8200

# PostgreSQL Configuration
POSTGRES_DB=cqrs_db
POSTGRES_USER=cqrs_user
POSTGRES_PASSWORD=local_dev_password
POSTGRES_PORT=5432

# Application Configuration
SPRING_PROFILES_ACTIVE=local
APP_PORT=8080
DEBUG_PORT=5005

# JVM Configuration
JAVA_OPTS=-Xms256m -Xmx512m

# Docker Configuration
COMPOSE_PROJECT_NAME=cqrs-spike
DOCKER_BUILDKIT=1
```

## Overriding Variables

### Command Line

```bash
# Single command
POSTGRES_PASSWORD=newpassword ./gradlew bootRun

# Export for session
export VAULT_TOKEN=custom-token
./gradlew bootRun
```

### In .env File

```bash
# .env (overrides defaults)
POSTGRES_PASSWORD=my-secure-password
APP_PORT=9090
```

### In docker-compose.override.yml

```yaml
# docker-compose.override.yml
services:
  postgres:
    environment:
      POSTGRES_PASSWORD: override-password
```

## Security Best Practices

### Development

- Use `.env` for local configuration
- Never commit `.env` to version control
- Keep secrets in Vault, not environment variables

### Production

- Use secrets management (Vault)
- Don't expose sensitive variables in logs
- Use read-only tokens where possible

## Troubleshooting

### Variable Not Applied

1. Check `.env` file exists and is readable
2. Restart Docker Compose: `docker compose restart`
3. Check variable precedence (env > .env > defaults)

### Wrong Value Used

```bash
# Debug: print resolved values
docker compose config

# Check specific variable
echo $POSTGRES_PASSWORD
```

### Permission Denied

```bash
# Ensure .env is readable
chmod 644 .env
```

## Variable Reference by Service

### Vault Service

```bash
VAULT_ROOT_TOKEN
VAULT_DEV_ROOT_TOKEN_ID
VAULT_DEV_LISTEN_ADDRESS
VAULT_ADDR
```

### PostgreSQL Service

```bash
POSTGRES_DB
POSTGRES_USER
POSTGRES_PASSWORD
```

### Application Service

```bash
SPRING_PROFILES_ACTIVE
VAULT_TOKEN
JAVA_OPTS
```

## See Also

- [Ports and URLs](ports-and-urls.md)
- [Commands Reference](commands.md)
- [Infrastructure Components](../architecture/infrastructure-components.md)
