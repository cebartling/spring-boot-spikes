# HashiCorp Vault Integration - Setup Guide

This guide explains how to use HashiCorp Vault for secrets management in the CQRS Spike application.

## Overview

The application uses HashiCorp Vault to securely store and manage sensitive configuration data such as:
- Database credentials
- API keys
- Encryption keys
- Service credentials (SMTP, Redis, etc.)

## Quick Start

### 1. Start Vault

```bash
# Start Vault container
docker-compose up -d vault

# Wait for Vault to be healthy
docker-compose ps vault
```

### 2. Initialize Secrets

```bash
# Run the initialization script
docker-compose up vault-init

# Verify secrets were created
docker-compose logs vault-init
```

### 3. Verify Vault is Running

```bash
# Check Vault health
curl http://localhost:8200/v1/sys/health

# Access Vault UI (optional)
open http://localhost:8200/ui
# Login with token: dev-root-token
```

### 4. Start the Application

```bash
# Build the application
./gradlew build

# Run the application
./gradlew bootRun

# Or use environment variables
export VAULT_URI=http://localhost:8200
export VAULT_TOKEN=dev-root-token
./gradlew bootRun
```

## Architecture

### Components

1. **Vault Container** (`docker-compose.yml:vault`)
   - Runs in development mode for ease of use
   - Exposes port 8200 for HTTP API and UI
   - Auto-unsealed and initialized

2. **Vault Initializer** (`docker-compose.yml:vault-init`)
   - One-time container that populates initial secrets
   - Runs the `init-secrets.sh` script

3. **Spring Cloud Vault** (Application integration)
   - Automatically loads secrets on startup
   - Configured via `bootstrap.yml` and `application.yml`

4. **SecretService** (Programmatic access)
   - Kotlin service for runtime secret retrieval
   - Provides type-safe access to secrets

## Configuration Files

### bootstrap.yml
Loaded before `application.yml` to configure Vault connection:
```yaml
spring:
  application:
    name: cqrs-spike
  cloud:
    vault:
      enabled: true
      uri: ${VAULT_URI:http://localhost:8200}
      token: ${VAULT_TOKEN:dev-root-token}
      authentication: TOKEN
```

### application.yml
Additional Vault configuration:
```yaml
spring:
  cloud:
    vault:
      kv:
        enabled: true
        backend: secret
        default-context: cqrs-spike
      fail-fast: true
      connection-timeout: 5000
      read-timeout: 15000
```

## Secret Paths

All secrets are stored under the `secret/cqrs-spike/` path:

| Path | Purpose | Example Keys |
|------|---------|--------------|
| `secret/cqrs-spike/database` | Database credentials | `username`, `password`, `url` |
| `secret/cqrs-spike/api-keys` | External API keys | `external-service-key`, `analytics-key` |
| `secret/cqrs-spike/encryption` | Encryption keys | `jwt-secret`, `aes-key` |
| `secret/cqrs-spike/services` | Service credentials | `smtp-username`, `redis-password` |

## Usage Examples

### Using SecretService (Programmatic Access)

```kotlin
import com.pintailconsultingllc.cqrsspike.service.SecretService
import org.springframework.stereotype.Component

@Component
class DatabaseConfig(private val secretService: SecretService) {

    fun getDatabaseUrl(): String {
        return secretService.getSecret("cqrs-spike/database", "url")
    }

    fun getAllDatabaseSecrets(): Map<String, Any> {
        return secretService.getAllSecrets("cqrs-spike/database")
    }
}
```

### Using Spring Property Injection

Secrets are automatically available as Spring properties:

```kotlin
@Component
class MyService {
    @Value("\${database.username}")
    private lateinit var dbUsername: String

    @Value("\${database.password}")
    private lateinit var dbPassword: String
}
```

### Using Vault CLI

```bash
# Set environment variables
export VAULT_ADDR='http://localhost:8200'
export VAULT_TOKEN='dev-root-token'

# Read a secret
vault kv get secret/cqrs-spike/database

# Read specific field
vault kv get -field=username secret/cqrs-spike/database

# List all secrets
vault kv list secret/cqrs-spike

# Add a new secret
vault kv put secret/cqrs-spike/custom key1=value1 key2=value2

# Update an existing secret
vault kv patch secret/cqrs-spike/database new-field=new-value

# Delete a secret
vault kv delete secret/cqrs-spike/custom
```

## Vault UI

Access the Vault UI at http://localhost:8200/ui

**Login credentials:**
- Method: Token
- Token: `dev-root-token`

The UI allows you to:
- Browse secrets
- Create/Update/Delete secrets
- View secret versions (KV v2)
- Manage policies
- View audit logs

## Security Considerations

### Development Mode (Current Setup)
- ✅ Easy to use and configure
- ✅ No unsealing required
- ✅ Auto-initialized
- ❌ Stores data in memory (lost on restart)
- ❌ Fixed root token
- ❌ No TLS encryption
- ❌ Not suitable for production

### Production Recommendations
When deploying to production:

1. **Disable Dev Mode**: Use a proper storage backend (Consul, etcd, cloud storage)
2. **Enable TLS**: Use HTTPS for all Vault communications
3. **Use Proper Authentication**: Implement AppRole, Kubernetes, or OIDC authentication
4. **Implement Policies**: Use fine-grained access control policies
5. **Enable Audit Logging**: Track all secret access
6. **Secret Rotation**: Implement automatic secret rotation
7. **High Availability**: Deploy multiple Vault instances
8. **Regular Backups**: Back up Vault data regularly

## Troubleshooting

### Vault Container Won't Start

```bash
# Check if port 8200 is already in use
lsof -i :8200

# View Vault logs
docker-compose logs vault

# Restart Vault
docker-compose restart vault
```

### Application Can't Connect to Vault

```bash
# Verify Vault is healthy
curl http://localhost:8200/v1/sys/health

# Check environment variables
echo $VAULT_URI
echo $VAULT_TOKEN

# Check application logs for connection errors
./gradlew bootRun --info
```

### Secrets Not Found

```bash
# Verify secrets were initialized
docker-compose logs vault-init

# List secrets manually
export VAULT_ADDR='http://localhost:8200'
export VAULT_TOKEN='dev-root-token'
vault kv list secret/cqrs-spike

# Re-initialize secrets
docker-compose up vault-init
```

### SecretService Throws SecretNotFoundException

```kotlin
// Check if secret exists first
if (secretService.secretExists("cqrs-spike/database", "username")) {
    val username = secretService.getSecret("cqrs-spike/database", "username")
}

// Or handle the exception
try {
    val secret = secretService.getSecret("path", "key")
} catch (e: SecretNotFoundException) {
    logger.error("Secret not found", e)
    // Use default value or fail gracefully
}
```

## Maintenance

### Viewing Logs

```bash
# Vault server logs
docker-compose logs -f vault

# Initialization logs
docker-compose logs vault-init
```

### Restarting Vault

```bash
# Restart Vault (data in dev mode is lost)
docker-compose restart vault

# Re-initialize secrets after restart
docker-compose up vault-init
```

### Stopping Vault

```bash
# Stop Vault
docker-compose stop vault

# Stop and remove Vault
docker-compose down vault
```

## Additional Resources

- [HashiCorp Vault Documentation](https://www.vaultproject.io/docs)
- [Spring Cloud Vault Reference](https://docs.spring.io/spring-cloud-vault/docs/current/reference/html/)
- [Vault Best Practices](https://learn.hashicorp.com/tutorials/vault/production-hardening)
- [Implementation Plan](documentation/plans/AC1-secrets-management.md)

## Next Steps

After setting up Vault, you may want to:

1. **Add Database Integration**: Store database credentials in Vault (AC3)
2. **Configure Message Queue**: Store RabbitMQ credentials in Vault (AC4)
3. **Set up Redis**: Store Redis password in Vault (AC5)
4. **Implement Secret Rotation**: Create a scheduled task to rotate secrets
5. **Add Integration Tests**: Write tests that verify Vault connectivity
