package com.pintailconsultingllc.cdcdebezium.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.datatype.jsr310.JavaTimeModule
import tools.jackson.module.kotlin.kotlinModule

@Configuration
class JacksonConfig {
    @Bean
    fun objectMapper(): ObjectMapper = JsonMapper.builder()
        .addModule(kotlinModule())
        .addModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()
}
