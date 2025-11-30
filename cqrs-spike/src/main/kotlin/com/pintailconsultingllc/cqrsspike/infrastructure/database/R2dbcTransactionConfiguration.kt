package com.pintailconsultingllc.cqrsspike.infrastructure.database

import io.r2dbc.spi.ConnectionFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.transaction.ReactiveTransactionManager

/**
 * R2DBC transaction manager configuration.
 *
 * This configuration ensures the R2DBC transaction manager is used as the primary
 * transaction manager for the application, since this is a reactive WebFlux application
 * that uses R2DBC for database operations.
 *
 * The JDBC DataSource and its transaction manager are still available for blocking
 * operations (e.g., Flyway migrations), but reactive @Transactional annotations
 * will use this R2DBC transaction manager by default.
 */
@Configuration
class R2dbcTransactionConfiguration {

    /**
     * Creates the primary ReactiveTransactionManager for R2DBC operations.
     *
     * This bean is marked as @Primary to resolve the ambiguity between
     * the JDBC transactionManager (from DataSource) and the R2DBC
     * connectionFactoryTransactionManager.
     *
     * @param connectionFactory The R2DBC connection factory (auto-configured)
     * @return Primary ReactiveTransactionManager for @Transactional annotations
     */
    @Bean
    @Primary
    fun reactiveTransactionManager(connectionFactory: ConnectionFactory): ReactiveTransactionManager {
        return R2dbcTransactionManager(connectionFactory)
    }
}
