package com.pintailconsultingllc.cdcdebezium.config

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.ReactiveMongoTransactionManager
import org.springframework.data.mongodb.config.AbstractReactiveMongoConfiguration
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories
import java.util.concurrent.TimeUnit

@Configuration
@EnableReactiveMongoRepositories(basePackages = ["com.pintailconsultingllc.cdcdebezium.repository"])
class MongoConfig : AbstractReactiveMongoConfiguration() {

    @Value("\${spring.data.mongodb.uri}")
    private lateinit var mongoUri: String

    @Value("\${spring.data.mongodb.database}")
    private lateinit var databaseName: String

    override fun getDatabaseName(): String = databaseName

    @Bean
    override fun reactiveMongoClient(): MongoClient {
        val connectionString = ConnectionString(mongoUri)

        val settings = MongoClientSettings.builder()
            .applyConnectionString(connectionString)
            .applyToConnectionPoolSettings { builder ->
                builder
                    .maxSize(20)
                    .minSize(5)
                    .maxWaitTime(30, TimeUnit.SECONDS)
            }
            .applyToSocketSettings { builder ->
                builder
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
            }
            .build()

        return MongoClients.create(settings)
    }

    @Bean
    fun reactiveMongoTemplate(
        factory: ReactiveMongoDatabaseFactory,
        converter: MappingMongoConverter
    ): ReactiveMongoTemplate {
        converter.setTypeMapper(DefaultMongoTypeMapper(null))
        return ReactiveMongoTemplate(factory, converter)
    }

    @Bean
    fun transactionManager(factory: ReactiveMongoDatabaseFactory): ReactiveMongoTransactionManager {
        return ReactiveMongoTransactionManager(factory)
    }
}
