package com.pintailconsultingllc.cdcdebezium.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

@Configuration
class JacksonConfig {
    @Bean
    fun objectMapper(): ObjectMapper = JsonMapper.builder()
        .addModule(kotlinModule())
        // Handle schema evolution gracefully
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        // Timestamp handling
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build()
}
