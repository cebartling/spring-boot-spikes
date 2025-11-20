# Test Documentation

This directory contains unit tests for the Resiliency Spike application.

## Test Structure

```
test/kotlin/com/pintailconsultingllc/resiliencyspike/
├── fixtures/
│   └── TestFixtures.kt              # Test data builders
├── service/
│   └── ResilienceEventServiceTest.kt  # Service layer tests
└── repository/
    ├── ResilienceEventRepositoryTest.kt       # Repository tests
    ├── CircuitBreakerStateRepositoryTest.kt
    └── RateLimiterMetricsRepositoryTest.kt
```

## Testing Framework

- **JUnit 5** - Test framework
- **MockitoExtension** - Dependency mocking
- **Mockito Kotlin** - Kotlin-friendly mocking DSL
- **Reactor Test** - Reactive stream testing with StepVerifier

## Test Patterns

### 1. MockitoExtension

All tests use `@ExtendWith(MockitoExtension::class)` to enable Mockito:

```kotlin
@ExtendWith(MockitoExtension::class)
@DisplayName("MyService Unit Tests")
class MyServiceTest {

    @Mock
    private lateinit var myRepository: MyRepository

    private lateinit var myService: MyService

    @BeforeEach
    fun setUp() {
        myService = MyService(myRepository)
    }
}
```

### 2. DisplayName Annotations

Every test class and method uses `@DisplayName` for clear descriptions:

```kotlin
@Test
@DisplayName("Should save event successfully when valid data provided")
fun shouldSaveEventSuccessfully() {
    // test implementation
}
```

### 3. Reactive Testing with StepVerifier

Test reactive streams using `StepVerifier` from reactor-test:

```kotlin
@Test
@DisplayName("Should return Flux of events")
fun shouldReturnFluxOfEvents() {
    // Given
    whenever(repository.findAll()).thenReturn(Flux.just(event1, event2))

    // When
    val result = service.findAll()

    // Then
    StepVerifier.create(result)
        .expectNext(event1)
        .expectNext(event2)
        .verifyComplete()
}
```

### 4. Test Fixtures

Use `TestFixtures` object for creating test data consistently:

```kotlin
val event = TestFixtures.createResilienceEvent(
    eventType = "CIRCUIT_BREAKER",
    eventName = "test-service",
    status = "SUCCESS"
)
```

## Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.pintailconsultingllc.resiliencyspike.service.ResilienceEventServiceTest"

# Run specific test method
./gradlew test --tests "*.ResilienceEventServiceTest.shouldSaveResilienceEventSuccessfully"

# Run tests with detailed output
./gradlew test --info

# Run tests continuously (rerun on file change)
./gradlew test --continuous
```

## Test Coverage Areas

### Service Tests (ResilienceEventServiceTest)
- Saving events
- Finding events by various criteria (type, status, ID)
- Finding recent events
- Error handling
- Edge cases (empty results, null values)

### Repository Tests
Each repository test suite covers:
- Basic CRUD operations (save, find, delete)
- Custom query methods
- Filtering and sorting
- Edge cases and empty results
- Type-specific queries

## Writing New Tests

When adding new tests:

1. **Use MockitoExtension** - Always extend with `MockitoExtension::class`
2. **Add DisplayName** - Use descriptive `@DisplayName` annotations
3. **Follow AAA Pattern** - Arrange, Act, Assert with clear comments
4. **Use StepVerifier** - For testing reactive Mono/Flux results
5. **Verify Interactions** - Use `verify()` to ensure mocks were called
6. **Test Edge Cases** - Include tests for empty results, errors, null values
7. **Use Test Fixtures** - Leverage `TestFixtures` for consistent test data

### Example Test Template

```kotlin
@Test
@DisplayName("Should [expected behavior] when [condition]")
fun shouldDoSomethingWhenConditionMet() {
    // Given
    val input = TestFixtures.createResilienceEvent()
    whenever(repository.save(any())).thenReturn(Mono.just(input))

    // When
    val result = service.save(input)

    // Then
    StepVerifier.create(result)
        .expectNextMatches { it.id != null }
        .verifyComplete()

    verify(repository).save(input)
}
```

## Best Practices

- Keep tests focused on a single behavior
- Use meaningful test names that describe the scenario
- Mock external dependencies, don't use real databases in unit tests
- Test both happy paths and error scenarios
- Keep test data minimal and relevant to the test case
- Use `StepVerifier` for all reactive stream assertions
- Verify mock interactions to ensure proper delegation
