# Vault Troubleshooting

This guide covers HashiCorp Vault-specific issues and solutions.

## Quick Diagnostics

```bash
# Check Vault status
curl http://localhost:8200/v1/sys/health

# View Vault logs
make logs-vault

# Check container status
docker ps | grep vault

# Test secret access
./scripts/vault-get.sh secret/cqrs-spike/database
```

## Common Issues

### Vault Not Starting

**Symptoms:**
- Container exits immediately
- Healthcheck failing
- Cannot connect on port 8200

**Check logs:**
```bash
docker logs cqrs-vault
```

**Common causes and solutions:**

1. **Port 8200 in use:**
   ```bash
   lsof -i :8200
   kill -9 <PID>
   docker compose restart vault
   ```

2. **Memory issues:**
   - Increase Docker memory allocation
   - Restart Docker Desktop

3. **Volume permissions:**
   ```bash
   chmod -R 755 infrastructure/vault/
   docker compose restart vault
   ```

### Vault Sealed

**Note:** In dev mode, Vault should never be sealed. If sealed:

**Symptoms:**
```json
{"initialized": true, "sealed": true}
```

**Solution:**
```bash
# In dev mode, restart recreates unsealed state
docker compose restart vault
```

### Secrets Not Found

**Symptoms:**
```
No value found at secret/data/cqrs-spike/database
```

**Check:**
```bash
# List secrets
vault kv list secret/cqrs-spike

# Verify initialization ran
docker logs cqrs-vault-init
```

**Solution:**
```bash
# Reinitialize secrets
docker compose up vault-init
```

### Permission Denied (403)

**Symptoms:**
```
permission denied
```

**Check token:**
```bash
echo $VAULT_TOKEN
# Should be: dev-root-token
```

**Solution:**
```bash
export VAULT_TOKEN='dev-root-token'
vault kv get secret/cqrs-spike/database
```

### Connection Refused

**Symptoms:**
```
connection refused
```

**Check:**
```bash
# Is Vault running?
docker ps | grep vault

# Is it healthy?
docker inspect cqrs-vault --format '{{.State.Health.Status}}'

# Can you reach the port?
nc -zv localhost 8200
```

**Solutions:**

1. **Start Vault:**
   ```bash
   docker compose up -d vault
   ```

2. **Wait for healthcheck:**
   ```bash
   # Wait up to 60 seconds
   timeout 60 bash -c 'until curl -s http://localhost:8200/v1/sys/health; do sleep 2; done'
   ```

3. **Restart Vault:**
   ```bash
   docker compose restart vault
   ```

### Application Cannot Connect to Vault

**Symptoms:**
- Application fails to start
- Vault-related errors in application logs

**Check from application perspective:**
```bash
# If app is in container
docker exec cqrs-app curl http://vault:8200/v1/sys/health

# Check app logs
./scripts/logs.sh app | grep -i vault
```

**Common issues:**

1. **Wrong Vault URL:**
   - Application should use `http://vault:8200` (Docker internal)
   - Not `http://localhost:8200` when in container

2. **Vault not ready:**
   - Application started before Vault was healthy
   - Solution: Restart application after Vault is healthy

3. **Token incorrect:**
   - Check `application.yml` or environment variables
   - Verify `VAULT_TOKEN` is set correctly

### Secrets Not Loading into Application

**Symptoms:**
- Application starts but has null/empty config values
- `@Value` injection fails

**Check Spring Cloud Vault config:**
```yaml
# application.yml
spring:
  cloud:
    vault:
      uri: http://vault:8200
      token: ${VAULT_TOKEN:dev-root-token}
      kv:
        enabled: true
        backend: secret
        default-context: cqrs-spike
```

**Verify secrets path:**
```bash
# Secrets should be at this exact path
vault kv get secret/cqrs-spike/database
```

**Check for path mismatch:**
- KV v2 uses `secret/data/` internally
- But you access via `secret/cqrs-spike/`

### Vault Init Container Fails

**Symptoms:**
- `cqrs-vault-init` exits with error
- Secrets not created

**Check logs:**
```bash
docker logs cqrs-vault-init
```

**Common causes:**

1. **Vault not ready yet:**
   - Init runs before Vault is healthy
   - Solution: Check `depends_on` condition

2. **Script error:**
   - Check `infrastructure/vault/scripts/init-secrets.sh`
   - Verify script syntax

3. **Permission denied:**
   ```bash
   chmod +x infrastructure/vault/scripts/*.sh
   ```

**Manual re-run:**
```bash
docker compose up vault-init
```

## Advanced Troubleshooting

### Enable Debug Logging

```bash
# Run Vault with debug
docker compose exec vault vault server -dev -log-level=debug
```

### Check Vault Audit Log

```bash
# If audit logging is enabled
docker exec cqrs-vault cat /vault/logs/audit.log
```

### Inspect Vault Configuration

```bash
# Enter Vault shell
make shell-vault

# Check mounted secret engines
vault secrets list

# Check auth methods
vault auth list

# Check policies
vault policy list
```

### Network Debugging

```bash
# Check Vault is reachable from host
curl -v http://localhost:8200/v1/sys/health

# Check from another container
docker exec cqrs-postgres curl http://vault:8200/v1/sys/health
```

## Reset Procedures

### Soft Reset (Preserve Data)

```bash
docker compose restart vault
# Wait for healthy
make health
```

### Full Reset (Clean State)

```bash
# Stop and remove
docker compose down
docker volume rm cqrs-vault-data 2>/dev/null || true

# Start fresh
docker compose up -d vault
# Wait for healthy, then init
docker compose up vault-init
```

### Reset Using Script

```bash
./scripts/reset-service.sh vault
```

## Vault CLI Reference

```bash
# Set environment
export VAULT_ADDR='http://localhost:8200'
export VAULT_TOKEN='dev-root-token'

# Status
vault status

# Read secret
vault kv get secret/cqrs-spike/database

# Write secret
vault kv put secret/cqrs-spike/custom key=value

# List secrets
vault kv list secret/cqrs-spike

# Delete secret
vault kv delete secret/cqrs-spike/custom

# Check token
vault token lookup
```

## Health Check Details

**Vault health endpoint:** `GET /v1/sys/health`

| Status Code | Meaning |
|-------------|---------|
| 200 | Initialized, unsealed, active |
| 429 | Unsealed and standby |
| 472 | DR secondary |
| 501 | Not initialized |
| 503 | Sealed |

**Expected in dev mode:**
```json
{
  "initialized": true,
  "sealed": false,
  "standby": false,
  "performance_standby": false,
  "replication_performance_mode": "disabled",
  "replication_dr_mode": "disabled",
  "server_time_utc": 1234567890,
  "version": "1.x.x",
  "cluster_name": "vault-cluster-xxxxx",
  "cluster_id": "xxxxx-xxxx-xxxx-xxxx-xxxxx"
}
```

## See Also

- [Secrets Management Guide](../guides/secrets-management.md)
- [Security Architecture](../architecture/security.md)
- [VAULT_SETUP.md](../../VAULT_SETUP.md)
- [Common Issues](common-issues.md)
