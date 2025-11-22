# Feature: Secrets Management with Vault

**Epic:** Platform Security
**Status:** Implemented
**Priority:** Critical

## User Story

**As a** security engineer
**I want** sensitive configuration and credentials stored securely
**So that** secrets are not exposed in code or configuration files

## Description

Secrets management integrates with HashiCorp Vault to securely store and retrieve sensitive configuration including database credentials, API keys, and application secrets. All secrets are fetched at application startup and injected into Spring configuration, eliminating hardcoded credentials.

## Acceptance Criteria

### Vault Integration
- [ ] Given Vault server, when application starts, then it connects to Vault using configured endpoint and token
- [ ] Given Vault connection failure, when application starts, then it fails fast with clear error message
- [ ] Given Vault configuration, when it is loaded, then Spring Cloud Vault manages the connection

### Secret Retrieval
- [ ] Given database secrets path, when application starts, then database credentials are fetched from Vault
- [ ] Given R2DBC secrets path, when application starts, then R2DBC connection details are fetched
- [ ] Given Pulsar secrets path, when application starts, then Pulsar configuration is fetched
- [ ] Given application secrets path, when application starts, then custom application secrets are fetched

### Secret Injection
- [ ] Given fetched secrets, when Spring context initializes, then secrets are injected into configuration properties
- [ ] Given injected secrets, when services use them, then they reference property placeholders (not hardcoded values)
- [ ] Given missing secrets, when application starts, then it fails with clear indication of missing secret

### Development Mode
- [ ] Given dev mode Vault, when running locally, then dev root token is used
- [ ] Given dev mode secrets, when they are missing, then reasonable defaults allow local development

## Business Rules

1. No secrets shall be committed to version control
2. All database credentials must be retrieved from Vault
3. Vault token must be provided via environment variable or secure file
4. Application must fail to start if required secrets are unavailable
5. Secrets are read-only from application perspective
6. Secret paths follow convention: `secret/resiliency-spike/{category}`

## Secret Categories

### Database Secrets (`secret/resiliency-spike/database`)
- Username
- Password
- Connection details

### R2DBC Secrets (`secret/resiliency-spike/r2dbc`)
- Connection URL
- Pool configuration
- Driver settings

### Pulsar Secrets (`secret/resiliency-spike/pulsar`)
- Broker URL
- Authentication credentials
- Namespace configuration

### Application Secrets (`secret/resiliency-spike/application`)
- API keys
- Custom application configuration
- Feature flags

## Out of Scope

- Dynamic secret rotation
- Vault secret versioning
- Multi-environment secret management (handled externally)
- Secret encryption/decryption in application
- Vault policy management
- Database credential dynamic generation
- Secret auditing (handled by Vault)

## Technical Notes

- Spring Cloud Vault provides integration
- bootstrap.properties configures Vault connection
- Dev mode uses static root token for convenience
- Production requires proper Vault authentication method
- Vault health indicator disabled to prevent startup issues
- Secrets cached for application lifetime (no runtime refresh)
