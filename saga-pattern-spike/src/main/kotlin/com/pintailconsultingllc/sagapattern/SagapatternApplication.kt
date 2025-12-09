package com.pintailconsultingllc.sagapattern

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SagapatternApplication

fun main(args: Array<String>) {
	runApplication<SagapatternApplication>(*args)
}
