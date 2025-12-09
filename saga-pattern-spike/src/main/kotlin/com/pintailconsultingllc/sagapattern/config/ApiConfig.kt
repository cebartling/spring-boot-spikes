package com.pintailconsultingllc.sagapattern.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for API settings.
 * These values are injected from Vault's KV secrets engine at path:
 * secret/sagapattern/application
 */
@ConfigurationProperties(prefix = "api")
data class ApiConfig(
    /**
     * Encryption key for sensitive data operations.
     * Stored in Vault at: secret/sagapattern/application/api.encryption-key
     */
    var encryptionKey: String = ""
)
