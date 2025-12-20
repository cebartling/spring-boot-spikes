package com.pintailconsultingllc.cdcdebezium.config

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy

@Configuration
class PlaywrightTestConfig {

    @Value("\${observability.jaeger.url:http://localhost:16686}")
    lateinit var jaegerUrl: String

    @Value("\${observability.prometheus.url:http://localhost:9090}")
    lateinit var prometheusUrl: String

    @Value("\${observability.otel-collector.otlp-http-url:http://localhost:4318}")
    lateinit var otlpHttpUrl: String

    @Value("\${playwright.headless:true}")
    var headless: Boolean = true

    @Bean
    @Lazy
    fun playwright(): Playwright = Playwright.create()

    @Bean
    @Lazy
    fun browser(playwright: Playwright): Browser {
        return playwright.chromium().launch(
            BrowserType.LaunchOptions().setHeadless(headless)
        )
    }
}
