# Secrets Management Guide

This guide covers working with HashiCorp Vault for secrets management in the CQRS Spike application.

## Overview

The application uses HashiCorp Vault for:
- Database credentials
- API keys
- Encryption keys
- Service-to-service authentication

## Vault Architecture

### Development Mode

In local development, Vault runs in dev mode:
- **No persistence:** Secrets are stored in memory
- **Auto-unsealed:** Always unsealed and ready
- **Root token:** Uses `dev-root-token`
- **UI enabled:** Available at http://localhost:8200/ui

### Secret Paths

All application secrets are stored under `secret/cqrs-spike/`:

| Path | Purpose |
|------|---------|
| `secret/cqrs-spike/database` | PostgreSQL credentials |
| `secret/cqrs-spike/api-keys` | External API keys |
| `secret/cqrs-spike/encryption` | Encryption keys |
| `secret/cqrs-spike/services` | Service credentials |

## Accessing Vault

### Using Vault UI

1. Open http://localhost:8200/ui
2. Enter token: `dev-root-token`
3. Navigate to: **Secrets** > **secret** > **cqrs-spike**
4. Browse and manage secrets

### Using CLI Script

```bash
# Get database secrets
./scripts/vault-get.sh secret/cqrs-spike/database

# Get API keys
./scripts/vault-get.sh secret/cqrs-spike/api-keys

# List all cqrs-spike secrets
./scripts/vault-get.sh list
```

### Using Vault CLI Directly

```bash
# Set environment
export VAULT_ADDR='http://localhost:8200'
export VAULT_TOKEN='dev-root-token'

# Get a secret
vault kv get secret/cqrs-spike/database

# Get specific field
vault kv get -field=password secret/cqrs-spike/database

# List secrets
vault kv list secret/cqrs-spike

# Put a secret
vault kv put secret/cqrs-spike/custom key=value

# Delete a secret
vault kv delete secret/cqrs-spike/custom
```

### Using Vault Shell

```bash
# Open shell in Vault container
make shell-vault

# In shell, environment is pre-configured
vault kv get secret/cqrs-spike/database
```

## Application Integration

### Property Injection

The application uses Spring Cloud Vault for property injection:

```kotlin
@Configuration
class DatabaseConfig {
    @Value("\${database.username}")
    private lateinit var username: String

    @Value("\${database.password}")
    private lateinit var password: String
}
```

### Programmatic Access

For dynamic secret retrieval:

```kotlin
@Service
class SecretService(
    private val vaultTemplate: VaultTemplate
) {
    fun getSecret(path: String, key: String): String {
        val response = vaultTemplate.read("secret/data/$path")
        val data = response?.data?.get("data") as? Map<*, *>
        return data?.get(key)?.toString()
            ?: throw SecretNotFoundException("Secret not found: $path/$key")
    }
}
```

### Configuration

Application configuration in `application.yml`:

```yaml
spring:
  cloud:
    vault:
      uri: http://localhost:8200
      token: ${VAULT_TOKEN:dev-root-token}
      kv:
        enabled: true
        backend: secret
        default-context: cqrs-spike
```

## Secret Structure

### Database Secrets

Path: `secret/cqrs-spike/database`

| Key | Description | Example |
|-----|-------------|---------|
| `username` | Database user | `cqrs_user` |
| `password` | Database password | `local_dev_password` |
| `url` | JDBC connection URL | `jdbc:postgresql://postgres:5432/cqrs_db` |

### API Keys

Path: `secret/cqrs-spike/api-keys`

| Key | Description |
|-----|-------------|
| `external-api-key` | Key for external service |
| `webhook-secret` | Webhook verification secret |

### Encryption Keys

Path: `secret/cqrs-spike/encryption`

| Key | Description |
|-----|-------------|
| `aes-key` | AES encryption key |
| `jwt-secret` | JWT signing secret |

## Managing Secrets

### Adding New Secrets

**Using UI:**
1. Navigate to secret path
2. Click "Create secret"
3. Add key-value pairs
4. Save

**Using CLI:**
```bash
export VAULT_ADDR='http://localhost:8200'
export VAULT_TOKEN='dev-root-token'

# Add single key
vault kv put secret/cqrs-spike/custom key=value

# Add multiple keys
vault kv put secret/cqrs-spike/custom \
    key1=value1 \
    key2=value2

# Add from file
vault kv put secret/cqrs-spike/custom @secrets.json
```

### Updating Secrets

```bash
# Get current values
vault kv get secret/cqrs-spike/database

# Update (replaces all values)
vault kv put secret/cqrs-spike/database \
    username=cqrs_user \
    password=new_password \
    url=jdbc:postgresql://postgres:5432/cqrs_db

# Patch (update single field)
vault kv patch secret/cqrs-spike/database password=new_password
```

### Deleting Secrets

```bash
# Soft delete (can be recovered)
vault kv delete secret/cqrs-spike/custom

# Permanent delete all versions
vault kv destroy -versions=all secret/cqrs-spike/custom
```

## Secret Initialization

### Automatic Initialization

When infrastructure starts, the `vault-init` container:
1. Waits for Vault to be healthy
2. Enables the KV secrets engine
3. Creates initial secrets from template

The initialization script is at `infrastructure/vault/scripts/init-secrets.sh`.

### Manual Re-initialization

```bash
# Reset Vault (loses all secrets)
./scripts/reset-service.sh vault

# Wait for Vault to restart
make health

# Secrets are re-initialized automatically
```

### Custom Initialization

To add custom secrets on startup, edit `infrastructure/vault/scripts/init-secrets.sh`:

```bash
# Add custom secrets
vault kv put secret/cqrs-spike/custom \
    api-key="my-api-key" \
    secret="my-secret"
```

## Security Best Practices

### Development Environment

- Use dev-root-token only for local development
- Never commit actual secrets to version control
- Use `.env` for local configuration overrides

### Secret Rotation

For local development, secrets can be rotated:

```bash
# Generate new password
NEW_PASS=$(openssl rand -base64 32)

# Update in Vault
vault kv patch secret/cqrs-spike/database password="$NEW_PASS"

# Restart application to pick up changes
# (or use dynamic secrets with Spring Cloud Vault)
```

### Access Patterns

The application should:
- Request only needed secrets
- Cache secrets appropriately
- Handle secret refresh gracefully
- Log access (not values) for audit

## Troubleshooting

### Cannot Connect to Vault

```bash
# Check Vault status
curl http://localhost:8200/v1/sys/health

# Check container
docker ps | grep vault

# View logs
make logs-vault

# Restart Vault
./scripts/reset-service.sh vault
```

### Secrets Not Found

```bash
# List available secrets
vault kv list secret/cqrs-spike

# Check specific path
vault kv get secret/cqrs-spike/database

# Re-initialize if needed
./scripts/reset-service.sh vault
```

### Permission Denied

```bash
# Check token
echo $VAULT_TOKEN

# Use correct token
export VAULT_TOKEN='dev-root-token'

# Try again
vault kv get secret/cqrs-spike/database
```

### Application Can't Access Vault

```bash
# Check Vault is running
make health

# Check application config
grep -A5 vault src/main/resources/application.yml

# Check network
docker exec cqrs-app curl http://vault:8200/v1/sys/health
```

## See Also

- [VAULT_SETUP.md](../../VAULT_SETUP.md) - Detailed Vault configuration
- [Architecture Overview](../architecture/overview.md) - System architecture
- [Security](../architecture/security.md) - Security model
- [Troubleshooting Vault](../troubleshooting/vault-issues.md) - Vault problems
