# Vault Infrastructure

This directory contains the configuration and scripts for HashiCorp Vault secrets management.

## Directory Structure

```
vault/
├── config/           # Vault server configuration
├── data/            # Vault data storage (gitignored)
├── scripts/         # Initialization and management scripts
└── policies/        # Vault access policies
```

## Quick Start

1. Start Vault using Docker Compose:
   ```bash
   docker-compose up -d vault
   ```

2. Initialize secrets:
   ```bash
   docker-compose up vault-init
   ```

3. Access Vault UI:
   - URL: http://localhost:8200/ui
   - Token: `dev-root-token`

## Accessing Secrets

### Via Vault CLI

```bash
# Set Vault address and token
export VAULT_ADDR='http://localhost:8200'
export VAULT_TOKEN='dev-root-token'

# Read database credentials
vault kv get secret/cqrs-spike/database

# List all secrets
vault kv list secret/cqrs-spike
```

### Via Vault UI

1. Navigate to http://localhost:8200/ui
2. Login with token: `dev-root-token`
3. Browse to `secret/cqrs-spike`

## Secret Paths

- `secret/cqrs-spike/database` - Database credentials
- `secret/cqrs-spike/api-keys` - External API keys
- `secret/cqrs-spike/encryption` - Encryption and signing keys
- `secret/cqrs-spike/services` - Service credentials (SMTP, Redis, etc.)

## Important Notes

- **Development Mode**: Vault runs in dev mode for local development
- **Security**: Root token is hardcoded for convenience (NEVER do this in production)
- **Persistence**: Dev mode uses in-memory storage; data is lost on restart
- **No TLS**: TLS is disabled for local development

## Troubleshooting

### Vault not starting
```bash
# Check logs
docker logs cqrs-vault

# Verify port availability
lsof -i :8200
```

### Secrets not initialized
```bash
# Re-run initialization
docker-compose up vault-init

# Manually initialize
docker exec -e VAULT_ADDR='http://localhost:8200' \
            -e VAULT_TOKEN='dev-root-token' \
            cqrs-vault sh /vault/scripts/init-secrets.sh
```
