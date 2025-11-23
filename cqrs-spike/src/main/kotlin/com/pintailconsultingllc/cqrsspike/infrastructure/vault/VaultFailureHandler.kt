package com.pintailconsultingllc.cqrsspike.infrastructure.vault

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationFailedEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component
import org.springframework.vault.VaultException

/**
 * Application listener that handles Vault-related startup failures.
 *
 * This component listens for application startup failures and provides
 * helpful error messages and troubleshooting steps when Vault connectivity
 * issues are detected.
 */
@Component
class VaultFailureHandler : ApplicationListener<ApplicationFailedEvent> {

    private val logger = LoggerFactory.getLogger(VaultFailureHandler::class.java)

    override fun onApplicationEvent(event: ApplicationFailedEvent) {
        val exception = event.exception

        if (isVaultRelated(exception)) {
            logger.error("=".repeat(80))
            logger.error("APPLICATION STARTUP FAILED - VAULT CONNECTION ERROR")
            logger.error("=".repeat(80))
            logger.error("The application failed to start due to Vault connectivity issues.")
            logger.error("Please ensure:")
            logger.error("  1. Vault service is running: docker ps | grep vault")
            logger.error("  2. Vault is accessible: curl http://localhost:8200/v1/sys/health")
            logger.error("  3. VAULT_TOKEN environment variable is set correctly")
            logger.error("  4. Required secrets exist in Vault")
            logger.error("=".repeat(80))
            logger.error("Error details:", exception)
        }
    }

    /**
     * Checks if the exception is related to Vault connectivity.
     *
     * @param exception The exception to check
     * @return true if the exception is Vault-related, false otherwise
     */
    private fun isVaultRelated(exception: Throwable?): Boolean {
        if (exception == null) {
            return false
        }

        val message = exception.message
        return exception is VaultException ||
                (message != null && (
                        message.contains("Vault") ||
                                message.contains("vault") ||
                                message.contains("8200")
                        )) ||
                isVaultRelated(exception.cause)
    }
}
