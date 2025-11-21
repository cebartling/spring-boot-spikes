package com.pintailconsultingllc.resiliencyspike

import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.vault.config.VaultAutoConfiguration
import org.springframework.cloud.vault.config.VaultReactiveAutoConfiguration

@SpringBootTest
@EnableAutoConfiguration(exclude = [VaultAutoConfiguration::class, VaultReactiveAutoConfiguration::class])
class ResiliencySpikeApplicationTests {

    @Test
    fun contextLoads() {
    }

}
