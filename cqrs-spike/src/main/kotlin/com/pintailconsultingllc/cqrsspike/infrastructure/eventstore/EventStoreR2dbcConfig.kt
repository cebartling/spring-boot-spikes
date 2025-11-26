package com.pintailconsultingllc.cqrsspike.infrastructure.eventstore

import io.r2dbc.postgresql.codec.Json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.data.r2dbc.dialect.PostgresDialect

/**
 * R2DBC configuration for event store JSONB handling.
 */
@Configuration
class EventStoreR2dbcConfig {

    @Bean
    @Primary
    fun r2dbcCustomConversions(): R2dbcCustomConversions {
        val converters = listOf(
            JsonToStringConverter(),
            StringToJsonConverter()
        )
        return R2dbcCustomConversions.of(PostgresDialect.INSTANCE, converters)
    }
}

/**
 * Converter from R2DBC Json to String.
 */
@ReadingConverter
class JsonToStringConverter : Converter<Json, String> {
    override fun convert(source: Json): String {
        return source.asString()
    }
}

/**
 * Converter from String to R2DBC Json.
 */
@WritingConverter
class StringToJsonConverter : Converter<String, Json> {
    override fun convert(source: String): Json {
        return Json.of(source)
    }
}
