# HashiCorp Vault Integration

## Status: ✅ COMPLETED

**Implementation Date:** December 2025
**PR:** #64

## Overview

This document describes the implementation plan for integrating HashiCorp Vault into the saga pattern spike application for centralized secret management. Vault will manage database credentials, API keys, and other sensitive configuration values.

## Goals

- **Centralize secret management** - Remove hardcoded credentials from configuration files
- **Dynamic credentials** - Use Vault's database secrets engine for rotating PostgreSQL credentials
- **Secure development workflow** - Provide local Vault instance for development parity
- **Production readiness** - Implement AppRole authentication for production deployments
- **Audit trail** - Enable secret access logging for compliance

## Architecture

### Current State

```mermaid
flowchart TB
    subgraph Docker Compose
        PG[(PostgreSQL)]
        WM[WireMock]
    end

    subgraph Application
        APP[Spring Boot App]
        CONFIG[application.yml<br/>hardcoded credentials]
    end

    CONFIG --> APP
    APP -->|hardcoded creds| PG
    APP --> WM
```

### Target State

```mermaid
flowchart TB
    subgraph Docker Compose
        subgraph Vault Service
            V[HashiCorp Vault]
            V --> KV[KV Secrets Engine]
            V --> DB[Database Secrets Engine]
        end

        PG[(PostgreSQL)]
        WM[WireMock]
    end

    subgraph Application
        APP[Spring Boot App]
        VCL[Spring Cloud Vault]
    end

    VCL -->|fetch secrets| V
    DB -->|dynamic creds| PG
    APP --> VCL
    APP --> PG
    APP --> WM
```

## Secret Storage Strategy

### KV Secrets Engine (v2)

Static secrets stored in Vault's key-value store:

| Path | Description | Properties |
|------|-------------|------------|
| `secret/sagapattern/application` | Common application secrets | API keys, encryption keys |
| `secret/sagapattern/dev` | Development profile secrets | Dev-specific config |
| `secret/sagapattern/prod` | Production profile secrets | Prod-specific config |

### Database Secrets Engine

Dynamic PostgreSQL credentials with automatic rotation:

| Role | Capabilities | TTL | Max TTL |
|------|--------------|-----|---------|
| `sagapattern-readwrite` | SELECT, INSERT, UPDATE, DELETE | 1 hour | 24 hours |
| `sagapattern-readonly` | SELECT | 1 hour | 24 hours |

## Authentication Strategy

### Development Environment

| Method | Use Case | Configuration |
|--------|----------|---------------|
| Token | Local development | Root token from dev Vault |
| AppRole | Testing production auth | Pre-configured role/secret |

### Production Environment

| Method | Use Case | Configuration |
|--------|----------|---------------|
| AppRole | Application authentication | Role ID + Secret ID |
| Kubernetes | K8s deployments | Service account JWT |

## Implementation Phases

### Phase 1: Local Vault Infrastructure

Add Vault to Docker Compose for local development.

#### 1.1 Update docker-compose.yml

Add Vault service with development configuration:

```yaml
vault:
  image: hashicorp/vault:1.15
  container_name: saga-vault
  ports:
    - "8200:8200"
  environment:
    VAULT_DEV_ROOT_TOKEN_ID: dev-root-token
    VAULT_DEV_LISTEN_ADDRESS: 0.0.0.0:8200
  cap_add:
    - IPC_LOCK
  healthcheck:
    test: ["CMD", "vault", "status"]
    interval: 10s
    timeout: 5s
    retries: 3
```

#### 1.2 Create Vault Initialization Script

Create `docker/vault/init.sh` to configure Vault on startup:

```bash
#!/bin/sh
# Wait for Vault to be ready
until vault status 2>/dev/null; do
  sleep 1
done

# Enable KV secrets engine v2
vault secrets enable -path=secret kv-v2

# Enable database secrets engine
vault secrets enable database

# Configure PostgreSQL connection
vault write database/config/postgresql \
  plugin_name=postgresql-database-plugin \
  allowed_roles="sagapattern-readwrite,sagapattern-readonly" \
  connection_url="postgresql://{{username}}:{{password}}@saga-postgres:5432/saga_db?sslmode=disable" \
  username="saga_user" \
  password="saga_password"

# Create readwrite role
vault write database/roles/sagapattern-readwrite \
  db_name=postgresql \
  creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; \
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO \"{{name}}\"; \
    GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO \"{{name}}\";" \
  revocation_statements="DROP ROLE IF EXISTS \"{{name}}\";" \
  default_ttl="1h" \
  max_ttl="24h"

# Create readonly role
vault write database/roles/sagapattern-readonly \
  db_name=postgresql \
  creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; \
    GRANT SELECT ON ALL TABLES IN SCHEMA public TO \"{{name}}\";" \
  revocation_statements="DROP ROLE IF EXISTS \"{{name}}\";" \
  default_ttl="1h" \
  max_ttl="24h"

# Write static secrets
vault kv put secret/sagapattern/application \
  api.encryption-key="dev-encryption-key-32-bytes-long"

# Enable AppRole auth for testing
vault auth enable approle

# Create application role
vault write auth/approle/role/sagapattern \
  token_ttl=1h \
  token_max_ttl=4h \
  secret_id_ttl=0 \
  policies="sagapattern-policy"

# Create policy
vault policy write sagapattern-policy - <<EOF
path "secret/data/sagapattern/*" {
  capabilities = ["read", "list"]
}
path "database/creds/sagapattern-readwrite" {
  capabilities = ["read"]
}
path "database/creds/sagapattern-readonly" {
  capabilities = ["read"]
}
EOF

echo "Vault initialization complete!"
```

#### 1.3 Tasks

| Task | Description | Files | Status |
|------|-------------|-------|--------|
| 1.1.1 | Add Vault service to docker-compose.yml | `docker-compose.yml` | ✅ |
| 1.1.2 | Create Vault config directory | `docker/vault/` | ✅ |
| 1.1.3 | Create initialization script | `docker/vault/init.sh` | ✅ |
| 1.1.4 | Update PostgreSQL to allow dynamic roles | `docker/postgres/init/01-schema.sql` | ✅ |
| 1.1.5 | Test Vault starts and initializes correctly | Manual verification | ✅ |

### Phase 2: Spring Cloud Vault Integration

Add Spring Cloud Vault dependencies and configuration.

#### 2.1 Add Dependencies

Update `build.gradle.kts`:

```kotlin
extra["springCloudVersion"] = "2024.0.0"

dependencies {
    // Existing dependencies...

    // Spring Cloud Vault
    implementation("org.springframework.cloud:spring-cloud-starter-vault-config")

    // Optional: Database credential rotation support
    implementation("org.springframework.cloud:spring-cloud-vault-config-databases")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}
```

#### 2.2 Application Configuration

Update `src/main/resources/application.yaml`:

```yaml
spring:
  application:
    name: sagapattern
  config:
    import: optional:vault://
  cloud:
    vault:
      enabled: true
      host: localhost
      port: 8200
      scheme: http
      authentication: TOKEN
      token: dev-root-token
      kv:
        enabled: true
        backend: secret
        default-context: sagapattern
        profile-separator: /
      database:
        enabled: true
        role: sagapattern-readwrite
        backend: database
        username-property: spring.r2dbc.username
        password-property: spring.r2dbc.password
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/saga_db
    # username and password injected by Vault

saga:
  services:
    inventory:
      base-url: http://localhost:8081/api/inventory
    payment:
      base-url: http://localhost:8081/api/payments
    shipping:
      base-url: http://localhost:8081/api/shipments
```

#### 2.3 Profile-Specific Configuration

Create `src/main/resources/application-prod.yaml`:

```yaml
spring:
  cloud:
    vault:
      scheme: https
      authentication: APPROLE
      app-role:
        role-id: ${VAULT_ROLE_ID}
        secret-id: ${VAULT_SECRET_ID}
      ssl:
        trust-store: ${VAULT_TRUST_STORE:classpath:vault-truststore.jks}
        trust-store-password: ${VAULT_TRUST_STORE_PASSWORD}
```

#### 2.4 Tasks

| Task | Description | Files | Status |
|------|-------------|-------|--------|
| 2.1.1 | Add Spring Cloud BOM to build.gradle.kts | `build.gradle.kts` | ✅ |
| 2.1.2 | Add spring-cloud-starter-vault-config dependency | `build.gradle.kts` | ✅ |
| 2.1.3 | Add vault-config-databases dependency | `build.gradle.kts` | ✅ |
| 2.2.1 | Update application.yaml with Vault config | `application.yaml` | ✅ |
| 2.2.2 | Remove hardcoded credentials | `application.yaml` | ✅ |
| 2.3.1 | Create production profile config | `application-prod.yaml` | ✅ |
| 2.4.1 | Create Vault configuration class (if needed) | `VaultConfig.kt` | ⏭️ Skipped (not needed) |

### Phase 3: Database Secrets Engine Integration

Configure dynamic PostgreSQL credential generation.

#### 3.1 PostgreSQL Configuration for Dynamic Roles

Update `docker/postgres/init/01-schema.sql` to support dynamic role creation:

```sql
-- Grant necessary permissions for Vault to create roles
GRANT CREATE ON DATABASE saga_db TO saga_user;
ALTER USER saga_user CREATEDB CREATEROLE;
```

#### 3.2 Vault Database Connection

The database secrets engine connection is configured in the init script (Phase 1.2).

#### 3.3 Credential Lifecycle

```mermaid
sequenceDiagram
    participant App as Spring Boot App
    participant SCV as Spring Cloud Vault
    participant V as Vault
    participant PG as PostgreSQL

    App->>SCV: Application startup
    SCV->>V: Request database credentials<br/>(database/creds/sagapattern-readwrite)
    V->>PG: CREATE ROLE with expiry
    PG-->>V: Role created
    V-->>SCV: Return username/password
    SCV-->>App: Inject into R2DBC config
    App->>PG: Connect with dynamic creds

    Note over App,PG: Credential TTL: 1 hour

    loop Credential Renewal
        SCV->>V: Renew lease
        V-->>SCV: Extended TTL
    end
```

#### 3.4 Tasks

| Task | Description | Files | Status |
|------|-------------|-------|--------|
| 3.1.1 | Update PostgreSQL init to allow role creation | `01-schema.sql` | ✅ |
| 3.2.1 | Verify database secrets engine configuration | `init.sh` | ✅ |
| 3.3.1 | Test credential generation via CLI | Manual | ✅ |
| 3.3.2 | Test application connects with dynamic creds | Integration test | ✅ |

### Phase 4: KV Secrets Migration

Migrate static secrets to Vault KV store.

#### 4.1 Secret Inventory

| Current Location | Secret | Target Path |
|------------------|--------|-------------|
| docker-compose.yml | POSTGRES_PASSWORD | `database/creds/*` (dynamic) |
| application.yaml | API keys | `secret/sagapattern/application` |
| Environment vars | Service URLs | `secret/sagapattern/{profile}` |

#### 4.2 KV Secret Structure

```
secret/
└── sagapattern/
    ├── application         # Shared across all profiles
    │   └── api.encryption-key
    ├── dev                 # Development profile
    │   └── external.api-key
    └── prod                # Production profile
        └── external.api-key
```

#### 4.3 Accessing Secrets in Code

```kotlin
@Configuration
@ConfigurationProperties(prefix = "api")
data class ApiConfig(
    var encryptionKey: String = ""
)
```

Vault automatically maps `secret/sagapattern/application/api.encryption-key` to the property.

#### 4.4 Tasks

| Task | Description | Files | Status |
|------|-------------|-------|--------|
| 4.1.1 | Document all secrets requiring migration | This document | ✅ |
| 4.2.1 | Create secret structure in init script | `init.sh` | ✅ |
| 4.3.1 | Create configuration properties classes | `ApiConfig.kt`, `SagaServicesConfig.kt` | ✅ |
| 4.4.1 | Remove hardcoded secrets from codebase | Various | ✅ |

### Phase 5: Testing Strategy

Configure testing with and without Vault.

#### 5.1 Unit Tests (Vault Disabled)

```kotlin
@SpringBootTest
@TestPropertySource(properties = [
    "spring.cloud.vault.enabled=false",
    "spring.r2dbc.username=test_user",
    "spring.r2dbc.password=test_password"
])
class UnitTests {
    // Tests run without Vault
}
```

#### 5.2 Integration Tests (Vault Enabled)

```kotlin
@SpringBootTest
@ActiveProfiles("integration")
class VaultIntegrationTests {
    // Tests run with Vault
}
```

#### 5.3 Acceptance Tests Configuration

Update `src/test/resources/application-test.yaml`:

```yaml
spring:
  cloud:
    vault:
      enabled: false
  r2dbc:
    username: saga_user
    password: saga_password
```

#### 5.4 Tasks

| Task | Description | Files | Status |
|------|-------------|-------|--------|
| 5.1.1 | Create test configuration with Vault disabled | `application-test.yaml` | ✅ |
| 5.2.1 | Create integration test profile | `application-integration.yaml` | ⏭️ Deferred |
| 5.3.1 | Update Cucumber test configuration | `AcceptanceTestConfig.kt` | ✅ |
| 5.4.1 | Verify existing tests pass | `./gradlew test` | ✅ |

### Phase 6: Health and Monitoring

Add Vault health checks to actuator.

#### 6.1 Actuator Configuration

Update `application.yaml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  health:
    vault:
      enabled: true
  endpoint:
    health:
      show-details: when_authorized
```

#### 6.2 Add Actuator Dependency

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}
```

#### 6.3 Health Check Response

```json
{
  "status": "UP",
  "components": {
    "vault": {
      "status": "UP",
      "details": {
        "version": "1.15.0",
        "sealed": false,
        "standby": false
      }
    }
  }
}
```

#### 6.4 Tasks

| Task | Description | Files | Status |
|------|-------------|-------|--------|
| 6.1.1 | Add actuator dependency | `build.gradle.kts` | ✅ |
| 6.2.1 | Configure health endpoints | `application.yaml` | ✅ |
| 6.3.1 | Verify Vault health in actuator | Manual | ✅ |

## Implementation Summary

### Phase Dependency Graph

```mermaid
flowchart LR
    P1[Phase 1<br/>Infrastructure]
    P2[Phase 2<br/>Spring Cloud Vault]
    P3[Phase 3<br/>Database Secrets]
    P4[Phase 4<br/>KV Migration]
    P5[Phase 5<br/>Testing]
    P6[Phase 6<br/>Health Monitoring]

    P1 --> P2
    P2 --> P3
    P2 --> P4
    P3 --> P5
    P4 --> P5
    P2 --> P6
```

### Files to Create/Modify

| Action | File | Phase |
|--------|------|-------|
| Modify | `docker-compose.yml` | 1 |
| Create | `docker/vault/init.sh` | 1 |
| Modify | `docker/postgres/init/01-schema.sql` | 1, 3 |
| Modify | `build.gradle.kts` | 2, 6 |
| Modify | `src/main/resources/application.yaml` | 2, 6 |
| Create | `src/main/resources/application-prod.yaml` | 2 |
| Create | `src/test/resources/application-test.yaml` | 5 |
| Create | `src/main/kotlin/.../config/VaultConfig.kt` | 2 (optional) |
| Create | `src/main/kotlin/.../config/ApiConfig.kt` | 4 |

### Security Considerations

| Risk | Mitigation |
|------|------------|
| Root token in dev | Only for local development; use AppRole in prod |
| Secret exposure in logs | Configure log masking for sensitive properties |
| Credential rotation failures | Implement connection pool refresh on renewal |
| Vault unavailability | Use `optional:vault://` for graceful degradation |
| TLS certificate management | Use trust store for production Vault |

### Rollback Plan

If Vault integration causes issues:

1. Set `spring.cloud.vault.enabled=false`
2. Restore hardcoded credentials in application.yaml
3. Remove Vault from docker-compose.yml
4. Revert dependency changes

## References

- [Spring Cloud Vault Documentation](https://docs.spring.io/spring-cloud-vault/docs/current/reference/html/)
- [HashiCorp Vault Documentation](https://developer.hashicorp.com/vault/docs)
- [Vault Database Secrets Engine](https://developer.hashicorp.com/vault/docs/secrets/databases)
- [Vault KV Secrets Engine v2](https://developer.hashicorp.com/vault/docs/secrets/kv/kv-v2)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
