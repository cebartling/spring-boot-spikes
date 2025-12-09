package com.pintailconsultingllc.sagapattern

import com.pintailconsultingllc.sagapattern.config.ApiConfig
import com.pintailconsultingllc.sagapattern.config.SagaServicesConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(ApiConfig::class, SagaServicesConfig::class)
class SagapatternApplication

fun main(args: Array<String>) {
	runApplication<SagapatternApplication>(*args)
}
