package com.pintailconsultingllc.cdcdebezium.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Hooks

/**
 * Enables automatic context propagation between Reactor Context and ThreadLocal values (including MDC).
 *
 * This is essential for WebFlux/reactive applications where:
 * - MDC is ThreadLocal-based
 * - Reactor switches threads during async operations
 * - Without this, MDC values would be lost when threads change
 *
 * With automatic context propagation enabled, the Micrometer context-propagation library
 * automatically bridges Reactor Context with ThreadLocal, ensuring trace_id, span_id,
 * and custom MDC values are preserved across thread boundaries.
 *
 * @see <a href="https://spring.io/blog/2023/03/30/context-propagation-with-project-reactor-3-unified-bridging-between-reactive/">
 *     Context Propagation with Project Reactor 3</a>
 */
@Configuration
class ReactorContextPropagationConfig {

    private val logger = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun enableContextPropagation() {
        Hooks.enableAutomaticContextPropagation()
        logger.info("Enabled Reactor automatic context propagation for MDC support in reactive streams")
    }
}
