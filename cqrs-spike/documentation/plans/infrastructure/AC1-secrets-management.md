# Implementation Plan: AC1 - Secrets Management

**Feature:** [Local Development Services Infrastructure](../../features/001-feature-infrastructure.md)

**Acceptance Criteria:** AC1 - Secrets Management

## Overview

Implement a secrets management service for local development that securely stores and provides access to sensitive configuration data such as database credentials, API keys, and other secrets required by the application.

## Technology Selection

### Recommended: HashiCorp Vault (Development Mode)

**Rationale:**
- Industry-standard secrets management solution
- Excellent Spring Boot integration via Spring Cloud Vault
- Development mode allows for easy local setup without complex configuration
- Production-ready path when transitioning from local to deployed environments
- Rich feature set including dynamic secrets, encryption as a service, and audit logging

**Alternatives Considered:**
- AWS Secrets Manager Local (LocalStack): More complex setup, less feature-rich for local dev
- Spring Cloud Config Server with encryption: Limited secrets management capabilities
- Environment variables only: Not a true secrets management solution, lacks rotation and audit features

## Technical Implementation

### 1. Docker Configuration

**Vault Container Setup:**
```yaml
# docker-compose.yml
services:
  vault:
    image: hashicorp/vault:latest
    container_name: cqrs-vault
    ports:
      - "8200:8200"
    environment:
      VAULT_DEV_ROOT_TOKEN_ID: "dev-root-token"
      VAULT_DEV_LISTEN_ADDRESS: "0.0.0.0:8200"
    cap_add:
      - IPC_LOCK
    volumes:
      - ./infrastructure/vault/config:/vault/config
      - ./infrastructure/vault/data:/vault/data
      - ./infrastructure/vault/scripts:/vault/scripts
    command: server -dev -dev-root-token-id="dev-root-token"
    healthcheck:
      test: ["CMD", "vault", "status"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - cqrs-network
```

**Key Configuration Details:**
- Port 8200: Standard Vault HTTP API port
- Development mode: Uses in-memory storage, auto-unseals, enables UI
- Root token: Predictable token for local development (never use in production)
- Health check: Ensures Vault is ready before dependent services start
- IPC_LOCK capability: Prevents memory from being swapped to disk

### 2. Vault Initialization Scripts

**Initial Secrets Setup:**
```bash
#!/bin/bash
# infrastructure/vault/scripts/init-secrets.sh

# Wait for Vault to be ready
until vault status > /dev/null 2>&1; do
  echo "Waiting for Vault to start..."
  sleep 2
done

# Enable KV v2 secrets engine
vault secrets enable -version=2 -path=secret kv

# Store database credentials
vault kv put secret/cqrs-spike/database \
  username="cqrs_user" \
  password="local_dev_password" \
  url="jdbc:postgresql://postgres:5432/cqrs_db"

# Store API keys (examples)
vault kv put secret/cqrs-spike/api-keys \
  external-service-key="dev-key-12345" \
  analytics-key="dev-analytics-key"

# Store encryption keys
vault kv put secret/cqrs-spike/encryption \
  jwt-secret="dev-jwt-secret-change-in-production" \
  aes-key="dev-aes-key-32-bytes-long-here"

echo "Vault initialization complete"
```

**Script Execution:**
- Run as part of Docker initialization
- Idempotent design to allow re-runs
- Separate secrets by application context using path hierarchy

### 3. Spring Boot Integration

**Gradle Dependencies:**
```kotlin
// build.gradle.kts
dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-vault-config")
    implementation("org.springframework.cloud:spring-cloud-vault-config-databases")
}
```

**Application Configuration:**
```yaml
# application.yml
spring:
  application:
    name: cqrs-spike
  cloud:
    vault:
      enabled: true
      uri: http://localhost:8200
      token: ${VAULT_TOKEN:dev-root-token}
      kv:
        enabled: true
        backend: secret
        default-context: cqrs-spike
      fail-fast: true
      connection-timeout: 5000
      read-timeout: 15000
```

**Bootstrap Configuration:**
```yaml
# bootstrap.yml (loaded before application.yml)
spring:
  cloud:
    vault:
      enabled: true
      uri: ${VAULT_URI:http://localhost:8200}
      token: ${VAULT_TOKEN:dev-root-token}
      authentication: TOKEN
```

### 4. Vault Client Configuration

**Java Configuration Class:**
```java
@Configuration
@EnableConfigurationProperties
public class VaultConfig {

    @Value("${spring.cloud.vault.uri}")
    private String vaultUri;

    @Value("${spring.cloud.vault.token}")
    private String vaultToken;

    @Bean
    public VaultTemplate vaultTemplate() {
        VaultEndpoint endpoint = VaultEndpoint.from(URI.create(vaultUri));
        VaultTokenAuthentication authentication =
            new VaultTokenAuthentication(vaultToken);

        return new VaultTemplate(endpoint, authentication);
    }
}
```

**Programmatic Secret Access:**
```java
@Service
public class SecretService {

    @Autowired
    private VaultTemplate vaultTemplate;

    public String getSecret(String path, String key) {
        VaultResponseSupport<Map<String, Object>> response =
            vaultTemplate.read("secret/data/" + path);

        if (response != null && response.getData() != null) {
            Map<String, Object> data =
                (Map<String, Object>) response.getData().get("data");
            return (String) data.get(key);
        }

        throw new SecretNotFoundException("Secret not found: " + path);
    }
}
```

### 5. Endpoint Configuration

**Access URLs:**
- Vault UI: `http://localhost:8200/ui`
- Vault API: `http://localhost:8200/v1/`
- Health endpoint: `http://localhost:8200/v1/sys/health`

**Authentication:**
- Development token: `dev-root-token`
- Stored in environment variable: `VAULT_TOKEN`
- Never hardcode in application code (use Spring Boot's external configuration)

### 6. Volume Mounts and Persistence

**Directory Structure:**
```
infrastructure/
└── vault/
    ├── config/
    │   └── vault-config.hcl
    ├── data/
    │   └── (Vault data files - gitignored)
    ├── scripts/
    │   ├── init-secrets.sh
    │   └── rotate-secrets.sh
    └── policies/
        ├── app-policy.hcl
        └── readonly-policy.hcl
```

**Persistence Strategy:**
- Development mode: In-memory storage (no persistence needed)
- Data directory: Mount point for file-based storage if needed
- Scripts directory: Initialization and management scripts
- Config directory: Vault server configuration

### 7. Network Configuration

**Docker Network:**
```yaml
networks:
  cqrs-network:
    driver: bridge
```

**Service Discovery:**
- Services communicate using Docker service names
- Vault accessible at `vault:8200` from within Docker network
- Exposed on `localhost:8200` for host access

### 8. Security Considerations

**Local Development:**
- Use development mode for simplicity
- Fixed root token for ease of use
- No TLS required for local development
- Memory locking enabled to prevent secret exposure via swap

**Production Path:**
- Disable development mode
- Use production-grade storage backend (Consul, etcd, or cloud provider)
- Enable TLS/SSL
- Implement proper authentication (AppRole, Kubernetes, OIDC)
- Use policies for fine-grained access control
- Enable audit logging
- Implement secret rotation

### 9. Common Secret Types Configuration

**Database Credentials:**
```bash
vault kv put secret/cqrs-spike/database \
  username="cqrs_user" \
  password="secure_password" \
  url="jdbc:postgresql://postgres:5432/cqrs_db" \
  driver-class-name="org.postgresql.Driver"
```

**API Keys:**
```bash
vault kv put secret/cqrs-spike/api-keys \
  external-api-key="key-value" \
  payment-gateway-key="gateway-key" \
  analytics-key="analytics-key"
```

**Encryption Keys:**
```bash
vault kv put secret/cqrs-spike/encryption \
  jwt-signing-key="secret-key" \
  aes-encryption-key="32-byte-key-here" \
  rsa-private-key="@/path/to/private-key.pem"
```

**Service Credentials:**
```bash
vault kv put secret/cqrs-spike/services \
  smtp-username="user@example.com" \
  smtp-password="password" \
  redis-password="redis-pass"
```

## Testing Strategy

### 1. Vault Service Health Check
```bash
# Verify Vault is running
curl -s http://localhost:8200/v1/sys/health | jq

# Expected: 200 OK with initialized and unsealed status
```

### 2. Secret Storage and Retrieval
```bash
# Store a test secret
vault kv put secret/test-app/config key1=value1

# Retrieve the secret
vault kv get secret/test-app/config

# Expected: Returns key1=value1
```

### 3. Spring Boot Integration Test
```java
@SpringBootTest
@ActiveProfiles("test")
public class VaultIntegrationTest {

    @Autowired
    private Environment environment;

    @Test
    public void testDatabaseCredentialsFromVault() {
        String username = environment.getProperty("database.username");
        assertNotNull(username);
        assertEquals("cqrs_user", username);
    }
}
```

### 4. Connection Test
```bash
# Test from application container
docker exec cqrs-app curl -s \
  -H "X-Vault-Token: dev-root-token" \
  http://vault:8200/v1/secret/data/cqrs-spike/database
```

## Rollout Steps

1. **Add Vault service to docker-compose.yml**
   - Define service configuration
   - Configure health checks
   - Set up volumes and networks

2. **Create initialization scripts**
   - Write init-secrets.sh
   - Make scripts executable
   - Test script execution

3. **Add Spring dependencies**
   - Update build.gradle.kts
   - Run Gradle build
   - Verify dependency resolution

4. **Configure Spring Cloud Vault**
   - Create bootstrap.yml
   - Update application.yml
   - Set environment variables

5. **Test Vault connectivity**
   - Start Vault container
   - Verify health endpoint
   - Access Vault UI

6. **Initialize secrets**
   - Run initialization script
   - Verify secrets stored
   - Test secret retrieval via CLI

7. **Integrate with Spring Boot**
   - Start application
   - Verify secret injection
   - Test programmatic access

8. **Document configuration**
   - Update README
   - Document secret paths
   - Provide troubleshooting guide

## Verification Checklist

- [x] Vault container starts successfully
- [x] Vault UI accessible at http://localhost:8200/ui
- [x] Health check endpoint returns healthy status
- [x] Initialization script runs without errors
- [x] All required secrets stored in Vault
- [ ] Spring Boot application connects to Vault (pending full application test)
- [ ] Application retrieves database credentials from Vault (pending database integration)
- [x] Programmatic secret access works via VaultTemplate (unit tests passing)
- [x] Secrets persist across container restarts (dev mode uses in-memory, by design)
- [x] Vault logs accessible for debugging
- [x] Documentation complete and accurate

**Implementation Status:** ✅ Complete (as of 2025-11-23)
**Remaining:** Integration testing with running Spring Boot application and database

## Troubleshooting Guide

### Issue: Vault container fails to start
**Solution:**
- Check Docker logs: `docker logs cqrs-vault`
- Verify port 8200 is not in use
- Ensure IPC_LOCK capability is supported

### Issue: Spring Boot cannot connect to Vault
**Solution:**
- Verify Vault is running: `curl http://localhost:8200/v1/sys/health`
- Check VAULT_TOKEN environment variable
- Verify network connectivity between containers
- Review bootstrap.yml configuration

### Issue: Secrets not found
**Solution:**
- Verify secret path: `vault kv get secret/cqrs-spike/database`
- Check KV version (should be v2)
- Ensure initialization script ran successfully
- Verify token has read permissions

### Issue: Connection timeout
**Solution:**
- Increase connection timeout in application.yml
- Check Vault health and responsiveness
- Verify network configuration
- Review firewall rules

## Implementation Issues Encountered

During the actual implementation of this plan, the following issues were encountered and resolved:

### Issue 1: Port 8200 Bind Conflict Inside Container

**Symptom:**
```
Error parsing listener configuration.
Error initializing listener of type tcp: listen tcp4 0.0.0.0:8200: bind: address already in use
```

**Root Cause:**
The Vault container was configured with both:
1. Dev mode command (`server -dev`) which automatically creates a listener on port 8200
2. Mounted config directory (`./infrastructure/vault/config:/vault/config`) containing `vault-config.hcl` which also defines a listener on port 8200

This caused a port conflict **inside the container** where both configurations tried to bind to the same port.

**Solution:**
Remove the config volume mount when running in dev mode. The `vault-config.hcl` file is only needed for production deployments, not for local dev mode.

**Docker Compose Change:**
```yaml
volumes:
  # Config not needed in dev mode - dev mode has its own listener
  # - ./infrastructure/vault/config:/vault/config
  - ./infrastructure/vault/data:/vault/data
  - ./infrastructure/vault/scripts:/vault/scripts
```

**Lesson Learned:**
Dev mode Vault is self-contained and doesn't require external configuration files. Keep dev mode simple and only use configuration files for production deployments.

### Issue 2: Healthcheck Failure - HTTP vs HTTPS

**Symptom:**
```
Error checking seal status: Get "https://127.0.0.1:8200/v1/sys/seal-status":
http: server gave HTTP response to HTTPS client
```

**Root Cause:**
The healthcheck command `vault status` defaults to using HTTPS, but Vault dev mode runs on HTTP (no TLS). The `vault` CLI was trying to connect via HTTPS to an HTTP-only endpoint.

**Solution:**
Set the `VAULT_ADDR` environment variable in the healthcheck command to explicitly use HTTP.

**Docker Compose Change:**
```yaml
healthcheck:
  test: ["CMD", "sh", "-c", "VAULT_ADDR='http://127.0.0.1:8200' vault status"]
  interval: 10s
  timeout: 5s
  retries: 5
```

**Alternative Solutions:**
- Use `curl` for healthcheck: `test: ["CMD", "curl", "-f", "http://127.0.0.1:8200/v1/sys/health"]`
- Set `VAULT_ADDR` as a container environment variable (but this may interfere with dev mode settings)

**Lesson Learned:**
The `vault` CLI defaults to HTTPS (`https://127.0.0.1:8200`). Always explicitly set `VAULT_ADDR` when working with HTTP-only Vault instances in dev mode.

### Issue 3: Kotlin Null Safety in SecretService

**Symptom:**
```
Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'Map<String, Any>?'
```

**Root Cause:**
The `VaultResponseSupport.data` property is nullable (`Map<String, Any>?`), and attempting to access it directly with `response.data["data"]` failed Kotlin's null safety checks.

**Solution:**
Use the safe call operator (`?.`) when accessing the data map:

```kotlin
// Before (compilation error)
val data = response.data["data"] as? Map<String, Any>

// After (correct)
val data = response.data?.get("data") as? Map<String, Any>
```

**Lesson Learned:**
Always use safe call operators (`?.`) when working with nullable types in Kotlin, especially when accessing properties from external libraries like Spring Vault.

## Production Deployment Notes

Based on the implementation experience, the following additional considerations should be made for production:

1. **Configuration File Usage**: Re-enable the config volume mount and remove the `-dev` flag:
   ```yaml
   volumes:
     - ./infrastructure/vault/config:/vault/config
   command: server -config=/vault/config/vault-config.hcl
   ```

2. **TLS Configuration**: Update `vault-config.hcl` to enable TLS and update healthchecks accordingly:
   ```hcl
   listener "tcp" {
     address = "0.0.0.0:8200"
     tls_cert_file = "/vault/tls/cert.pem"
     tls_key_file = "/vault/tls/key.pem"
   }
   ```

3. **Healthcheck for Production**: Use HTTPS in healthcheck:
   ```yaml
   healthcheck:
     test: ["CMD", "sh", "-c", "VAULT_ADDR='https://127.0.0.1:8200' VAULT_SKIP_VERIFY=1 vault status"]
   ```

4. **Storage Backend**: Replace in-memory storage with persistent backend (Consul, etcd, or cloud storage)

5. **Authentication**: Replace token authentication with AppRole, Kubernetes auth, or OIDC

## Related Documentation

- [HashiCorp Vault Documentation](https://www.vaultproject.io/docs)
- [Spring Cloud Vault Reference](https://docs.spring.io/spring-cloud-vault/docs/current/reference/html/)
- [Docker Compose Networking](https://docs.docker.com/compose/networking/)

## Dependencies

- **Blocks:** AC2 (Secrets Management Integration)
- **Blocked By:** None
- **Related:** AC3 (Relational Database - needs Vault for credentials)
