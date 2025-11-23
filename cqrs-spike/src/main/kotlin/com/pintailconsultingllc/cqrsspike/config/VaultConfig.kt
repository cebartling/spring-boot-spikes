package com.pintailconsultingllc.cqrsspike.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.vault.authentication.TokenAuthentication
import org.springframework.vault.client.VaultEndpoint
import org.springframework.vault.core.VaultTemplate
import java.net.URI

/**
 * Configuration class for HashiCorp Vault integration.
 *
 * This class sets up the VaultTemplate bean for programmatic access to secrets
 * stored in Vault. It reads the Vault URI and token from application configuration.
 */
@Configuration
class VaultConfig {

    @Value("\${spring.cloud.vault.uri}")
    private lateinit var vaultUri: String

    @Value("\${spring.cloud.vault.token}")
    private lateinit var vaultToken: String

    /**
     * Creates a VaultTemplate bean for interacting with Vault.
     *
     * @return VaultTemplate configured with the Vault endpoint and authentication
     */
    @Bean
    fun vaultTemplate(): VaultTemplate {
        val endpoint = VaultEndpoint.from(URI.create(vaultUri))
        val authentication = TokenAuthentication(vaultToken)
        return VaultTemplate(endpoint, authentication)
    }
}
