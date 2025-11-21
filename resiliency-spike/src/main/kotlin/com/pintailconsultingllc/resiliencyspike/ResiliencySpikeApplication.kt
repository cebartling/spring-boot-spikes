package com.pintailconsultingllc.resiliencyspike

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.vault.config.VaultAutoConfiguration
import org.springframework.cloud.vault.config.VaultReactiveAutoConfiguration

@SpringBootApplication(exclude = [VaultAutoConfiguration::class, VaultReactiveAutoConfiguration::class])
class ResiliencySpikeApplication

fun main(args: Array<String>) {
    runApplication<ResiliencySpikeApplication>(*args)
}
