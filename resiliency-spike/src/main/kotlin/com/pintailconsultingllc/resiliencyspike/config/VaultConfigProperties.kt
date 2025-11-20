package com.pintailconsultingllc.resiliencyspike.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Configuration properties loaded from HashiCorp Vault
 *
 * This demonstrates that secrets are being successfully retrieved from Vault
 * and injected into the Spring application context.
 */
@Configuration
@ConfigurationProperties(prefix = "")
data class VaultConfigProperties(
    var username: String = "",
    var password: String = "",
    var database: String = "",
    var host: String = "",
    var port: String = "",
    var url: String = "",
    var environment: String = "",
    var name: String = ""
)
