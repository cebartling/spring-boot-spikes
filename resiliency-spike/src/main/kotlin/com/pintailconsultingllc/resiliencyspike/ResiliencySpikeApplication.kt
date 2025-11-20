package com.pintailconsultingllc.resiliencyspike

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ResiliencySpikeApplication

fun main(args: Array<String>) {
    runApplication<ResiliencySpikeApplication>(*args)
}
