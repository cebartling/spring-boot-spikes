package com.pintailconsultingllc.cqrsspike.infrastructure.eventstore

import io.r2dbc.postgresql.codec.Json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.data.r2dbc.dialect.PostgresDialect

/**
 * R2DBC configuration for event store JSONB handling.
 *
 * Note: Only the ReadingConverter (Json -> String) is registered globally.
 * The WritingConverter (String -> Json) is intentionally NOT registered globally
 * because it would incorrectly convert all String parameters to JSONB,
 * causing type mismatches with VARCHAR columns.
 *
 * For JSONB columns, the entity fields should use io.r2dbc.postgresql.codec.Json
 * type directly, or handle the conversion explicitly in repository methods.
 */
@Configuration
class EventStoreR2dbcConfig {

    @Bean
    @Primary
    fun r2dbcCustomConversions(): R2dbcCustomConversions {
        val converters = listOf(
            JsonToStringConverter()
            // Note: StringToJsonConverter is NOT registered globally to avoid
            // converting all String parameters to JSONB
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
