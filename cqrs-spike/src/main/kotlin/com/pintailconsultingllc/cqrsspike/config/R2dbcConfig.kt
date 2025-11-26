package com.pintailconsultingllc.cqrsspike.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories

/**
 * R2DBC configuration for reactive repositories.
 *
 * Explicitly enables R2DBC repositories and excludes them from JDBC
 * repository scanning to avoid conflicts when both are present.
 *
 * JDBC is configured to scan only a non-existent package to effectively
 * disable JDBC repository auto-detection while still allowing Flyway
 * migrations to work.
 */
@Configuration
@EnableJdbcRepositories(basePackages = [])
@EnableR2dbcRepositories(
    basePackages = [
        "com.pintailconsultingllc.cqrsspike.infrastructure.eventstore",
        "com.pintailconsultingllc.cqrsspike.product.command.infrastructure"
    ]
)
class R2dbcConfig
