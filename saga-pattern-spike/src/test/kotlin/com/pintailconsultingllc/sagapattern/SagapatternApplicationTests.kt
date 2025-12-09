package com.pintailconsultingllc.sagapattern

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@TestPropertySource(properties = [
	"spring.cloud.vault.enabled=false",
	"spring.r2dbc.username=saga_user",
	"spring.r2dbc.password=saga_password",
	"api.encryption-key=test-encryption-key-32-bytes-long"
])
class SagapatternApplicationTests {

	@Test
	fun contextLoads() {
	}
}

