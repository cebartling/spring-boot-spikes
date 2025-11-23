package com.pintailconsultingllc.cqrsspike.infrastructure.vault

import jakarta.annotation.PostConstruct
import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated

/**
 * Configuration properties for Vault integration.
 *
 * This class binds Vault configuration properties from application.yml/bootstrap.yml
 * and provides validation to ensure required properties are set.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "spring.cloud.vault")
data class VaultConfigurationProperties(
    @field:NotBlank(message = "Vault URI must be configured")
    var uri: String = "",

    @field:NotBlank(message = "Vault token must be configured")
    var token: String = "",

    var enabled: Boolean = true,

    var failFast: Boolean = true
) {
    private val logger = LoggerFactory.getLogger(VaultConfigurationProperties::class.java)

    @PostConstruct
    fun init() {
        if (!enabled) {
            logger.warn("Vault integration is DISABLED")
            return
        }

        logger.info("Vault Configuration:")
        logger.info("  URI: {}", uri)
        logger.info("  Token: {} (length: {})", maskToken(token), token.length)
        logger.info("  Fail Fast: {}", failFast)

        if (failFast) {
            logger.info("Application will fail to start if Vault is unavailable")
        } else {
            logger.warn("Application will start even if Vault is unavailable (fail-fast disabled)")
        }
    }

    /**
     * Masks the token for logging to prevent exposing sensitive data.
     *
     * @param token The token to mask
     * @return A masked version of the token showing only first and last 4 characters
     */
    private fun maskToken(token: String): String {
        if (token.length < 8) {
            return "***"
        }
        return "${token.substring(0, 4)}...${token.substring(token.length - 4)}"
    }
}
