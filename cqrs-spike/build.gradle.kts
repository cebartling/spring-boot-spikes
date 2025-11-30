plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management") version "1.1.7"
    jacoco
}

group = "com.pintailconsultingllc"
version = "0.0.1-SNAPSHOT"
description = "cqrs-spike"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

extra["springCloudVersion"] = "2025.1.0-RC1"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j")

    // Vault integration
    implementation("org.springframework.cloud:spring-cloud-starter-vault-config")
    implementation("org.springframework.cloud:spring-cloud-vault-config-databases")

    // Database - R2DBC PostgreSQL
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.postgresql:r2dbc-postgresql")
    runtimeOnly("org.postgresql:postgresql") // JDBC driver for migrations/tools

    // Flyway Database Migrations
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc") // Required for Flyway and JDBC DataSource

    // Connection pooling
    implementation("com.zaxxer:HikariCP")

    // Hypersistence Utils for JSONB support
    implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.7.0")

    // Observability - Metrics
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Observability - Observation API for unified tracing and metrics (AC11)
    implementation("io.micrometer:micrometer-observation")

    // Observability - Tracing (OpenTelemetry)
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    // AOP support for @Observed annotation (AC11)
    implementation("org.springframework:spring-aop")
    implementation("org.aspectj:aspectjweaver")

    // Observability - Structured Logging
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    // OpenAPI/Swagger Documentation
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.8")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webflux-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("io.projectreactor:reactor-test")

    // Testcontainers for integration testing
    testImplementation("org.testcontainers:testcontainers:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.testcontainers:r2dbc:1.20.4")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.12"
}

// Common JaCoCo exclusions for classes that require integration tests or are infrastructure
val jacocoExclusions = listOf(
    "**/dto/**",
    "**/config/**",
    "**/CqrsSpikeApplication*",
    // Infrastructure classes requiring Docker/integration tests
    "**/infrastructure/database/**",
    "**/infrastructure/vault/**",
    "**/infrastructure/eventstore/**",
    "**/infrastructure/observability/**",
    "**/product/command/infrastructure/**",
    "**/product/command/service/**",
    // Domain services requiring repository access (integration tests only)
    "**/ValidationDomainService*",
    // Projection infrastructure classes requiring Docker/integration tests
    "**/ProjectionRunner*",
    "**/ProjectionOrchestrator*",
    "**/ProjectionConfig*",
    "**/ProjectionMetrics*",
    "**/EventQueryService*",
    "**/StoredEvent*",
    "**/ProjectionStatus*",
    // Admin API controllers that depend on projection infrastructure
    "**/ProjectionController*",
    // Exception handlers (tested via @WebFluxTest integration tests)
    "**/CommandExceptionHandler*",
    "**/QueryExceptionHandler*"
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        csv.required = false
        html.required = true
        html.outputLocation = layout.buildDirectory.dir("reports/jacoco/html")
        xml.outputLocation = layout.buildDirectory.file("reports/jacoco/jacocoTestReport.xml")
    }
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(jacocoExclusions)
            }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(jacocoExclusions)
            }
        })
    )
    violationRules {
        rule {
            limit {
                // AC12: Test coverage meets minimum threshold (80% line coverage)
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

// Add coverage verification to the build lifecycle (AC12)
tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
