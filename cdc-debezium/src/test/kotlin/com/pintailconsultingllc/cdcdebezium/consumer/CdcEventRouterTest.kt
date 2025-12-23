package com.pintailconsultingllc.cdcdebezium.consumer

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono

class CdcEventRouterTest {

    private lateinit var handler1: CdcEventHandler
    private lateinit var handler2: CdcEventHandler
    private lateinit var router: CdcEventRouter

    @BeforeEach
    fun setUp() {
        handler1 = mockk()
        handler2 = mockk()

        every { handler1.topic } returns "cdc.public.customer"
        every { handler1.entityType } returns "customer"
        every { handler2.topic } returns "cdc.public.address"
        every { handler2.entityType } returns "address"

        router = CdcEventRouter(listOf(handler1, handler2))
    }

    private fun createRecord(
        topic: String,
        key: String = "test-key",
        value: String = """{"id":"123"}"""
    ): ConsumerRecord<String, String> = ConsumerRecord(topic, 0, 0, key, value)

    @Test
    fun `router discovers all handlers on startup`() {
        assert(router.getHandlerCount() == 2)
    }

    @Test
    fun `getRegisteredTopics returns all topics`() {
        val topics = router.getRegisteredTopics()
        assert(topics.contains("cdc.public.customer"))
        assert(topics.contains("cdc.public.address"))
    }

    @Nested
    inner class Routing {

        @Test
        fun `routes customer event to CustomerEventHandler`() {
            val record = createRecord("cdc.public.customer")
            every { handler1.handle(record) } returns Mono.empty()

            router.route(record).block()

            verify(exactly = 1) { handler1.handle(record) }
            verify(exactly = 0) { handler2.handle(any()) }
        }

        @Test
        fun `routes address event to AddressEventHandler`() {
            val record = createRecord("cdc.public.address")
            every { handler2.handle(record) } returns Mono.empty()

            router.route(record).block()

            verify(exactly = 0) { handler1.handle(any()) }
            verify(exactly = 1) { handler2.handle(record) }
        }

        @Test
        fun `handles unknown topic gracefully`() {
            val record = createRecord("cdc.public.unknown")

            val result = router.route(record).block()

            assert(result == null)
            verify(exactly = 0) { handler1.handle(any()) }
            verify(exactly = 0) { handler2.handle(any()) }
        }
    }
}
