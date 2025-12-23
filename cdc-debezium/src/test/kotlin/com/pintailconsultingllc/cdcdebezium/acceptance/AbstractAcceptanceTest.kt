package com.pintailconsultingllc.cdcdebezium.acceptance

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@AcceptanceTest
@ActiveProfiles("acceptance")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractAcceptanceTest
