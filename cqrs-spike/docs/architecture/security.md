# Security Architecture

This document describes the security model and practices for the CQRS Spike application.

## Security Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Security Layers                           │
├─────────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    Application Security                    │  │
│  │  • Input validation  • Authentication  • Authorization    │  │
│  └───────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    Secrets Management                      │  │
│  │  • Vault integration  • Credential rotation  • Encryption │  │
│  └───────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    Infrastructure Security                 │  │
│  │  • Network isolation  • TLS  • Access controls            │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Secrets Management

### HashiCorp Vault Integration

All sensitive data is managed through Vault:

```
┌─────────────────┐         ┌─────────────────┐
│   Application   │◀────────│     Vault       │
│                 │  Token  │                 │
│  Spring Cloud   │────────▶│  KV Secrets     │
│  Vault Client   │ Secrets │  Engine v2      │
└─────────────────┘         └─────────────────┘
```

### Secret Categories

| Path | Contents | Rotation |
|------|----------|----------|
| `secret/cqrs-spike/database` | DB credentials | On change |
| `secret/cqrs-spike/api-keys` | External API keys | Periodic |
| `secret/cqrs-spike/encryption` | Encryption keys | Annual |
| `secret/cqrs-spike/services` | Service tokens | On demand |

### Secret Access Pattern

```kotlin
// Property injection (recommended)
@Value("\${database.password}")
private lateinit var dbPassword: String

// Programmatic access (when needed)
@Service
class SecretService(private val vault: VaultTemplate) {
    fun getApiKey(name: String): String {
        return vault.read("secret/data/cqrs-spike/api-keys")
            ?.data?.get("data")?.let { (it as Map<*, *>)[name] }?.toString()
            ?: throw SecretNotFoundException(name)
    }
}
```

### Development vs Production

| Aspect | Development | Production |
|--------|-------------|------------|
| Vault Mode | Dev mode | Cluster mode |
| Token | Root token | Service token |
| Storage | In-memory | Persistent |
| Unsealing | Auto | Manual/Auto-unseal |

## Database Security

### Credential Management

Database credentials are stored in Vault:

```bash
# Stored at: secret/cqrs-spike/database
{
  "username": "cqrs_user",
  "password": "local_dev_password",
  "url": "jdbc:postgresql://postgres:5432/cqrs_db"
}
```

### Connection Security

**Development:**
- Plain TCP (port 5432)
- Password authentication

**Production Considerations:**
- TLS encryption
- Certificate authentication
- Connection pooling limits

### Access Control

```sql
-- User permissions (managed by migrations)
GRANT ALL PRIVILEGES ON SCHEMA event_store TO cqrs_user;
GRANT ALL PRIVILEGES ON SCHEMA read_model TO cqrs_user;
GRANT ALL PRIVILEGES ON SCHEMA command_model TO cqrs_user;

-- Restrict to specific schemas
REVOKE ALL ON SCHEMA public FROM cqrs_user;
```

## Network Security

### Docker Network Isolation

```yaml
networks:
  cqrs-network:
    driver: bridge
    internal: false  # Set true for production
    ipam:
      config:
        - subnet: 172.28.0.0/16
```

### Service Exposure

| Service | Internal | External (Dev) | External (Prod) |
|---------|----------|----------------|-----------------|
| Vault | Yes | Yes (8200) | No (internal only) |
| PostgreSQL | Yes | Yes (5432) | No (internal only) |
| Application | Yes | Yes (8080) | Yes (via LB) |

### Firewall Recommendations

**Development:**
```bash
# Allow local access only
iptables -A INPUT -p tcp --dport 8080 -s 127.0.0.1 -j ACCEPT
iptables -A INPUT -p tcp --dport 8200 -s 127.0.0.1 -j ACCEPT
iptables -A INPUT -p tcp --dport 5432 -s 127.0.0.1 -j ACCEPT
```

## Application Security

### Input Validation

```kotlin
@RestController
class CommandController(private val service: CommandService) {

    @PostMapping("/products")
    fun createProduct(@Valid @RequestBody request: CreateProductRequest): Mono<Product> {
        return service.create(request)
    }
}

data class CreateProductRequest(
    @field:NotBlank
    val name: String,

    @field:Min(0)
    @field:Max(99999999)
    val priceCents: Int
)
```

### Error Handling

Avoid exposing internal details:

```kotlin
@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unhandled exception", ex)

        // Don't expose stack traces
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse("An error occurred"))
    }
}
```

### CORS Configuration

```kotlin
@Configuration
class WebConfig {

    @Bean
    fun corsConfigurer(): WebFluxConfigurer {
        return object : WebFluxConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                registry.addMapping("/api/**")
                    .allowedOrigins("http://localhost:3000")
                    .allowedMethods("GET", "POST", "PUT", "DELETE")
            }
        }
    }
}
```

## Resilience Security

### Circuit Breaker Protection

Prevents cascade failures:

```kotlin
@CircuitBreaker(name = "database", fallbackMethod = "fallback")
@Retry(name = "database")
fun findById(id: UUID): Mono<Product> {
    return repository.findById(id)
}
```

### Rate Limiting

Protects against abuse:

```kotlin
@RateLimiter(name = "api", fallbackMethod = "rateLimitFallback")
fun processCommand(command: Command): Mono<Result> {
    return commandService.process(command)
}
```

## Logging Security

### Sensitive Data Handling

Never log sensitive information:

```kotlin
// BAD - logs password
logger.info("Connecting with password: $password")

// GOOD - redacts sensitive data
logger.info("Connecting to database as user: $username")
```

### Audit Logging

```kotlin
@Aspect
@Component
class AuditAspect {

    @Around("@annotation(Audited)")
    fun auditMethod(joinPoint: ProceedingJoinPoint): Any? {
        val methodName = joinPoint.signature.name
        logger.info("Audit: Method $methodName called")

        return joinPoint.proceed().also {
            logger.info("Audit: Method $methodName completed")
        }
    }
}
```

## Security Best Practices

### Development Environment

1. **Use `.env` for local config** - Never commit secrets
2. **Use dev tokens only locally** - Different tokens per environment
3. **Rotate passwords periodically** - Even in development
4. **Review security logs** - Check for anomalies

### Code Security

1. **Validate all input** - Never trust user input
2. **Use parameterized queries** - R2DBC handles this
3. **Escape output** - Prevent XSS
4. **Handle errors gracefully** - Don't expose internals

### Dependency Security

```bash
# Check for vulnerabilities
./gradlew dependencyCheckAnalyze

# Update dependencies regularly
./gradlew dependencyUpdates
```

## Security Checklist

### Pre-Deployment

- [ ] All secrets in Vault
- [ ] No hardcoded credentials
- [ ] Input validation on all endpoints
- [ ] Error messages don't expose internals
- [ ] Logging doesn't include sensitive data
- [ ] Dependencies checked for vulnerabilities
- [ ] CORS properly configured
- [ ] Rate limiting enabled
- [ ] Circuit breakers configured

### Runtime

- [ ] Vault accessible and healthy
- [ ] Database credentials valid
- [ ] Audit logs enabled
- [ ] Health endpoints protected (if needed)
- [ ] Debug endpoints disabled in production

## Incident Response

### Credential Compromise

1. **Rotate immediately** in Vault
2. **Revoke old credentials**
3. **Restart affected services**
4. **Review access logs**
5. **Document incident**

### Service Compromise

1. **Isolate affected service**
2. **Collect logs and evidence**
3. **Rotate all credentials**
4. **Patch vulnerability**
5. **Restore service**
6. **Post-incident review**

## See Also

- [Secrets Management](../guides/secrets-management.md) - Vault usage
- [Architecture Overview](overview.md) - System design
- [VAULT_SETUP.md](../../VAULT_SETUP.md) - Vault configuration
- [Troubleshooting Vault](../troubleshooting/vault-issues.md) - Vault issues
