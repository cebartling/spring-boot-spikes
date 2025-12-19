package com.pintailconsultingllc.cdcdebezium

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CdcDebeziumApplication

fun main(args: Array<String>) {
    runApplication<CdcDebeziumApplication>(*args)
}
