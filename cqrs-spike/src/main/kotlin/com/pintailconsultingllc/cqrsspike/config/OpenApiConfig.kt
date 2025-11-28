package com.pintailconsultingllc.cqrsspike.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
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
                        - Query endpoints for retrieving products
                        - Command endpoints for creating and modifying products
                        - Support for pagination, filtering, and search

                        ## Query Side (Read Model)
                        All query endpoints operate on the read model which is
                        eventually consistent with the command model through
                        event projections.
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
    }
}
