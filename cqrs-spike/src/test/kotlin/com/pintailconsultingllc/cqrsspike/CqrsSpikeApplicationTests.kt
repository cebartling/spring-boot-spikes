package com.pintailconsultingllc.cqrsspike

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Basic application context loading test.
 * Verifies that the Spring context can start successfully.
 *
 * IMPORTANT: Before running this test, ensure Docker Compose
 * infrastructure is running:
 *   make start
 */
@SpringBootTest
@ActiveProfiles("test")
class CqrsSpikeApplicationTests {

    @Test
    fun contextLoads() {
    }

}
