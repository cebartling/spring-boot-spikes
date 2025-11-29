package com.pintailconsultingllc.cqrsspike.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI/Swagger documentation configuration.
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Product Catalog API")
                    .description(
                        """
                        Product Catalog REST API built with CQRS architecture.

                        This API provides:
                        - **Command endpoints** for creating and modifying products
                        - **Query endpoints** for retrieving products
                        - Support for pagination, filtering, and search

                        ## Command Side (Write Model)
                        Command endpoints modify product state and return the new version.
                        All commands support idempotency via the `Idempotency-Key` header.

                        ## Query Side (Read Model)
                        Query endpoints operate on the read model which is eventually
                        consistent with the command model through event projections.

                        ## Optimistic Concurrency
                        Update operations require an `expectedVersion` to prevent
                        concurrent modification conflicts.
                        """.trimIndent()
                    )
                    .version("1.0.0")
                    .contact(
                        Contact()
                            .name("Pintail Consulting LLC")
                            .url("https://pintailconsultingllc.com")
                    )
            )
            .servers(
                listOf(
                    Server()
                        .url("/")
                        .description("Default Server")
                )
            )
            .tags(
                listOf(
                    Tag()
                        .name("Product Queries")
                        .description("Endpoints for querying products from the read model"),
                    Tag()
                        .name("Product Commands")
                        .description("Endpoints for creating and modifying products")
                )
            )
            .components(
                Components()
                    .addSecuritySchemes(
                        "idempotencyKey",
                        SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY)
                            .`in`(SecurityScheme.In.HEADER)
                            .name("Idempotency-Key")
                            .description("Optional idempotency key for command requests (UUID format)")
                    )
            )
    }
}
