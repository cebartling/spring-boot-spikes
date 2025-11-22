# Implementation Plan: AC1 - Secrets Management

**Feature:** [Local Development Services Infrastructure](../features/001-feature-infrastructure.md)

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

**Maven Dependencies:**
```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-vault-config</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-vault-config-databases</artifactId>
</dependency>
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
   - Update pom.xml
   - Run Maven build
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

- [ ] Vault container starts successfully
- [ ] Vault UI accessible at http://localhost:8200/ui
- [ ] Health check endpoint returns healthy status
- [ ] Initialization script runs without errors
- [ ] All required secrets stored in Vault
- [ ] Spring Boot application connects to Vault
- [ ] Application retrieves database credentials from Vault
- [ ] Programmatic secret access works via VaultTemplate
- [ ] Secrets persist across container restarts (if using file backend)
- [ ] Vault logs accessible for debugging
- [ ] Documentation complete and accurate

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

## Related Documentation

- [HashiCorp Vault Documentation](https://www.vaultproject.io/docs)
- [Spring Cloud Vault Reference](https://docs.spring.io/spring-cloud-vault/docs/current/reference/html/)
- [Docker Compose Networking](https://docs.docker.com/compose/networking/)

## Dependencies

- **Blocks:** AC2 (Secrets Management Integration)
- **Blocked By:** None
- **Related:** AC3 (Relational Database - needs Vault for credentials)
