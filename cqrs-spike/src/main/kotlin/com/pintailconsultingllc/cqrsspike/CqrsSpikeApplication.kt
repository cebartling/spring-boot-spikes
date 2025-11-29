package com.pintailconsultingllc.cqrsspike

import com.pintailconsultingllc.cqrsspike.product.command.validation.BusinessRulesConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(BusinessRulesConfig::class)
class CqrsSpikeApplication

fun main(args: Array<String>) {
    runApplication<CqrsSpikeApplication>(*args)
}
