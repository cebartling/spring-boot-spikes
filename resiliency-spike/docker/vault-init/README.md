# Vault Initialization Scripts

This directory contains scripts for initializing HashiCorp Vault with application secrets for local development.

## Files

### `init-vault.sh`
Main initialization script that:
- Enables KV v2 secrets engine (if not already enabled)
- Creates application secrets at various paths
- Sets up Vault policies for the application
- Generates application tokens with appropriate permissions
- Verifies all secrets were created successfully

## Secret Structure

The script initializes secrets at the following paths:

### `secret/resiliency-spike/database`
Database connection details:
- `username` - Database username
- `password` - Database password
- `host` - Database hostname
- `port` - Database port
- `database` - Database name

### `secret/resiliency-spike/r2dbc`
R2DBC configuration:
- `url` - Full R2DBC connection URL
- `username` - Database username
- `password` - Database password
- `initial-size` - Connection pool initial size
- `max-size` - Connection pool max size

### `secret/resiliency-spike/pulsar`
Apache Pulsar configuration:
- `service-url` - Pulsar broker URL
- `admin-url` - Pulsar admin/HTTP URL

### `secret/resiliency-spike/application`
Application-level configuration:
- `name` - Application name
- `environment` - Environment name (development, staging, production)

## Access Control

The script creates a policy named `resiliency-spike-policy` that grants read access to all application secrets. This policy is used to generate application tokens with limited permissions.

## Development vs Production

**IMPORTANT:** This setup is for **LOCAL DEVELOPMENT ONLY**. It uses:
- Dev mode (unsealed, no storage persistence between restarts)
- Hardcoded root token (`dev-root-token`)
- Simplified policies

For production:
1. Use production mode with proper storage backend (Consul, etcd, etc.)
2. Implement proper unsealing procedures
3. Use dynamic secrets where possible
4. Implement secret rotation
5. Use AppRole or Kubernetes auth instead of tokens
6. Enable audit logging
7. Use separate Vault namespaces for isolation

## Modifying Secrets

To add or modify secrets, edit `init-vault.sh` and add new `vault kv put` commands. Then restart the vault-init container:

```bash
docker-compose down vault-init
docker-compose up -d vault-init
docker-compose logs vault-init
```

Or restart all services:

```bash
docker-compose down -v && docker-compose up -d
```

## Manual Secret Management

You can manually manage secrets using the Vault CLI:

```bash
# Set environment variables
export VAULT_ADDR=http://localhost:8200
export VAULT_TOKEN=dev-root-token

# Create/update a secret
vault kv put secret/resiliency-spike/database password=new_password

# Read a secret
vault kv get secret/resiliency-spike/database

# Get specific field
vault kv get -field=password secret/resiliency-spike/database

# List secrets
vault kv list secret/resiliency-spike

# Delete a secret
vault kv delete secret/resiliency-spike/database
```

Or using Docker exec:

```bash
docker exec -e VAULT_TOKEN=dev-root-token resiliency-spike-vault vault kv get secret/resiliency-spike/database
```

## Vault UI

Access the Vault web UI at: http://localhost:8200/ui

Login with token: `dev-root-token`
