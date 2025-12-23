package com.pintailconsultingllc.cdcdebezium.acceptance

import org.junit.jupiter.api.Tag

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Tag("acceptance")
annotation class AcceptanceTest
