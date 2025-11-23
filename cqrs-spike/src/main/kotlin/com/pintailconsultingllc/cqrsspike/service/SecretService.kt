package com.pintailconsultingllc.cqrsspike.service

import com.pintailconsultingllc.cqrsspike.exception.SecretNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.vault.core.VaultTemplate
import org.springframework.vault.support.VaultResponseSupport

/**
 * Service for retrieving secrets from HashiCorp Vault.
 *
 * This service provides programmatic access to secrets stored in Vault,
 * with proper error handling and logging.
 */
@Service
class SecretService(private val vaultTemplate: VaultTemplate) {

    private val logger = LoggerFactory.getLogger(SecretService::class.java)

    /**
     * Retrieves a specific secret value from Vault.
     *
     * @param path The path to the secret in Vault (e.g., "cqrs-spike/database")
     * @param key The specific key within the secret
     * @return The secret value as a String
     * @throws SecretNotFoundException if the secret is not found
     */
    fun getSecret(path: String, key: String): String {
        logger.debug("Retrieving secret from path: secret/data/$path, key: $key")

        try {
            val response: VaultResponseSupport<Map<String, Any>>? =
                vaultTemplate.read("secret/data/$path")

            if (response?.data != null) {
                @Suppress("UNCHECKED_CAST")
                val data = response.data?.get("data") as? Map<String, Any>
                val value = data?.get(key) as? String

                if (value != null) {
                    logger.debug("Successfully retrieved secret for key: $key")
                    return value
                }
            }

            logger.warn("Secret not found: $path/$key")
            throw SecretNotFoundException("Secret not found at path: $path, key: $key")
        } catch (e: Exception) {
            logger.error("Error retrieving secret from path: $path, key: $key", e)
            throw SecretNotFoundException("Failed to retrieve secret: ${e.message}", e)
        }
    }

    /**
     * Retrieves all secrets from a given path.
     *
     * @param path The path to the secrets in Vault (e.g., "cqrs-spike/database")
     * @return A map of all key-value pairs at the specified path
     * @throws SecretNotFoundException if the path is not found
     */
    fun getAllSecrets(path: String): Map<String, Any> {
        logger.debug("Retrieving all secrets from path: secret/data/$path")

        try {
            val response: VaultResponseSupport<Map<String, Any>>? =
                vaultTemplate.read("secret/data/$path")

            if (response?.data != null) {
                @Suppress("UNCHECKED_CAST")
                val data = response.data?.get("data") as? Map<String, Any>

                if (data != null) {
                    logger.debug("Successfully retrieved all secrets from path: $path")
                    return data
                }
            }

            logger.warn("No secrets found at path: $path")
            throw SecretNotFoundException("No secrets found at path: $path")
        } catch (e: Exception) {
            logger.error("Error retrieving secrets from path: $path", e)
            throw SecretNotFoundException("Failed to retrieve secrets: ${e.message}", e)
        }
    }

    /**
     * Checks if a secret exists at the specified path and key.
     *
     * @param path The path to the secret in Vault
     * @param key The specific key within the secret
     * @return true if the secret exists, false otherwise
     */
    fun secretExists(path: String, key: String): Boolean {
        return try {
            getSecret(path, key)
            true
        } catch (e: SecretNotFoundException) {
            false
        }
    }
}
