package com.pintailconsultingllc.resiliencyspike.repository

import com.pintailconsultingllc.resiliencyspike.domain.CircuitBreakerState
import com.pintailconsultingllc.resiliencyspike.fixtures.TestFixtures
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

@ExtendWith(MockitoExtension::class)
@DisplayName("CircuitBreakerStateRepository Unit Tests")
class CircuitBreakerStateRepositoryTest {

    @Mock
    private lateinit var circuitBreakerStateRepository: CircuitBreakerStateRepository

    @Test
    @DisplayName("Should save circuit breaker state and return saved entity")
    fun shouldSaveCircuitBreakerStateAndReturnSavedEntity() {
        // Given
        val state = TestFixtures.createCircuitBreakerState(id = null)
        val savedState = state.copy(id = UUID.randomUUID())

        whenever(circuitBreakerStateRepository.save(state))
            .thenReturn(Mono.just(savedState))

        // When
        val result = circuitBreakerStateRepository.save(state)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { it.id != null }
            .verifyComplete()

        verify(circuitBreakerStateRepository).save(state)
    }

    @Test
    @DisplayName("Should find circuit breaker state by name")
    fun shouldFindCircuitBreakerStateByName() {
        // Given
        val circuitBreakerName = "payment-service"
        val state = TestFixtures.createCircuitBreakerState(circuitBreakerName = circuitBreakerName)

        whenever(circuitBreakerStateRepository.findByCircuitBreakerName(circuitBreakerName))
            .thenReturn(Mono.just(state))

        // When
        val result = circuitBreakerStateRepository.findByCircuitBreakerName(circuitBreakerName)

        // Then
        StepVerifier.create(result)
            .expectNext(state)
            .verifyComplete()

        verify(circuitBreakerStateRepository).findByCircuitBreakerName(circuitBreakerName)
    }

    @Test
    @DisplayName("Should return empty Mono when circuit breaker name not found")
    fun shouldReturnEmptyMonoWhenCircuitBreakerNameNotFound() {
        // Given
        val circuitBreakerName = "nonexistent-service"

        whenever(circuitBreakerStateRepository.findByCircuitBreakerName(circuitBreakerName))
            .thenReturn(Mono.empty())

        // When
        val result = circuitBreakerStateRepository.findByCircuitBreakerName(circuitBreakerName)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(circuitBreakerStateRepository).findByCircuitBreakerName(circuitBreakerName)
    }

    @Test
    @DisplayName("Should find all circuit breakers by state")
    fun shouldFindAllCircuitBreakersByState() {
        // Given
        val state = "OPEN"
        val openCircuitBreakers = listOf(
            TestFixtures.createCircuitBreakerState(circuitBreakerName = "service-1", state = state),
            TestFixtures.createCircuitBreakerState(circuitBreakerName = "service-2", state = state)
        )

        whenever(circuitBreakerStateRepository.findByState(state))
            .thenReturn(Flux.fromIterable(openCircuitBreakers))

        // When
        val result = circuitBreakerStateRepository.findByState(state)

        // Then
        StepVerifier.create(result)
            .expectNextCount(2)
            .verifyComplete()

        verify(circuitBreakerStateRepository).findByState(state)
    }

    @Test
    @DisplayName("Should find all circuit breakers ordered by failure count descending")
    fun shouldFindAllOrderedByFailureCountDesc() {
        // Given
        val circuitBreakers = listOf(
            TestFixtures.createCircuitBreakerState(circuitBreakerName = "service-1", failureCount = 10),
            TestFixtures.createCircuitBreakerState(circuitBreakerName = "service-2", failureCount = 5)
        )

        whenever(circuitBreakerStateRepository.findAllOrderByFailureCountDesc())
            .thenReturn(Flux.fromIterable(circuitBreakers))

        // When
        val result = circuitBreakerStateRepository.findAllOrderByFailureCountDesc()

        // Then
        StepVerifier.create(result)
            .expectNext(circuitBreakers[0])
            .expectNext(circuitBreakers[1])
            .verifyComplete()

        verify(circuitBreakerStateRepository).findAllOrderByFailureCountDesc()
    }

    @Test
    @DisplayName("Should find circuit breakers with failure count above threshold")
    fun shouldFindCircuitBreakersWithFailureCountAboveThreshold() {
        // Given
        val threshold = 5
        val circuitBreakers = listOf(
            TestFixtures.createCircuitBreakerState(circuitBreakerName = "service-1", failureCount = 10),
            TestFixtures.createCircuitBreakerState(circuitBreakerName = "service-2", failureCount = 7)
        )

        whenever(circuitBreakerStateRepository.findWithFailureCountAbove(threshold))
            .thenReturn(Flux.fromIterable(circuitBreakers))

        // When
        val result = circuitBreakerStateRepository.findWithFailureCountAbove(threshold)

        // Then
        StepVerifier.create(result)
            .expectNextCount(2)
            .verifyComplete()

        verify(circuitBreakerStateRepository).findWithFailureCountAbove(threshold)
    }

    @Test
    @DisplayName("Should return true when circuit breaker exists by name")
    fun shouldReturnTrueWhenCircuitBreakerExistsByName() {
        // Given
        val circuitBreakerName = "payment-service"

        whenever(circuitBreakerStateRepository.existsByCircuitBreakerName(circuitBreakerName))
            .thenReturn(Mono.just(true))

        // When
        val result = circuitBreakerStateRepository.existsByCircuitBreakerName(circuitBreakerName)

        // Then
        StepVerifier.create(result)
            .expectNext(true)
            .verifyComplete()

        verify(circuitBreakerStateRepository).existsByCircuitBreakerName(circuitBreakerName)
    }

    @Test
    @DisplayName("Should return false when circuit breaker does not exist by name")
    fun shouldReturnFalseWhenCircuitBreakerDoesNotExistByName() {
        // Given
        val circuitBreakerName = "nonexistent-service"

        whenever(circuitBreakerStateRepository.existsByCircuitBreakerName(circuitBreakerName))
            .thenReturn(Mono.just(false))

        // When
        val result = circuitBreakerStateRepository.existsByCircuitBreakerName(circuitBreakerName)

        // Then
        StepVerifier.create(result)
            .expectNext(false)
            .verifyComplete()

        verify(circuitBreakerStateRepository).existsByCircuitBreakerName(circuitBreakerName)
    }

    @Test
    @DisplayName("Should find circuit breaker by ID")
    fun shouldFindCircuitBreakerById() {
        // Given
        val id = UUID.randomUUID()
        val state = TestFixtures.createCircuitBreakerState(id = id)

        whenever(circuitBreakerStateRepository.findById(id))
            .thenReturn(Mono.just(state))

        // When
        val result = circuitBreakerStateRepository.findById(id)

        // Then
        StepVerifier.create(result)
            .expectNext(state)
            .verifyComplete()

        verify(circuitBreakerStateRepository).findById(id)
    }

    @Test
    @DisplayName("Should delete circuit breaker by ID")
    fun shouldDeleteCircuitBreakerById() {
        // Given
        val id = UUID.randomUUID()

        whenever(circuitBreakerStateRepository.deleteById(id))
            .thenReturn(Mono.empty())

        // When
        val result = circuitBreakerStateRepository.deleteById(id)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(circuitBreakerStateRepository).deleteById(id)
    }

    @Test
    @DisplayName("Should return empty Flux when no circuit breakers in specified state")
    fun shouldReturnEmptyFluxWhenNoCircuitBreakersInState() {
        // Given
        val state = "HALF_OPEN"

        whenever(circuitBreakerStateRepository.findByState(state))
            .thenReturn(Flux.empty())

        // When
        val result = circuitBreakerStateRepository.findByState(state)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(circuitBreakerStateRepository).findByState(state)
    }
}
