plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "4.0.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.pintailconsultingllc"
version = "0.0.1-SNAPSHOT"
description = "cdc-debezium"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

repositories {
    mavenCentral()
}

val cucumberVersion = "7.20.1"

sourceSets {
    create("acceptanceTest") {
        kotlin.srcDir("src/acceptanceTest/kotlin")
        resources.srcDir("src/acceptanceTest/resources")
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    }
}

val acceptanceTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}

val acceptanceTestRuntimeOnly: Configuration by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")

    // OTel Metrics (PLAN-007)
    implementation("io.opentelemetry:opentelemetry-sdk-metrics")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    // Structured JSON logging with trace correlation (PLAN-008)
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    implementation("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:2.1.0-alpha")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("tools.jackson.module:jackson-module-kotlin")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.postgresql:r2dbc-postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-kafka-test")
    testImplementation("org.springframework.boot:spring-boot-starter-opentelemetry-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-r2dbc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo.spring3x:4.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Cucumber dependencies for acceptance testing
    acceptanceTestImplementation("io.cucumber:cucumber-java:$cucumberVersion")
    acceptanceTestImplementation("io.cucumber:cucumber-junit-platform-engine:$cucumberVersion")
    acceptanceTestImplementation("io.cucumber:cucumber-spring:$cucumberVersion")
    acceptanceTestImplementation("org.junit.platform:junit-platform-suite")

    // OpenTelemetry SDK testing for acceptance tests
    acceptanceTestImplementation("io.opentelemetry:opentelemetry-sdk-testing")

    // Playwright for UI acceptance testing (PLAN-009)
    acceptanceTestImplementation("com.microsoft.playwright:playwright:1.49.0")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<Test>("acceptanceTest") {
    description = "Runs acceptance tests with Cucumber."
    group = "verification"
    testClassesDirs = sourceSets["acceptanceTest"].output.classesDirs
    classpath = sourceSets["acceptanceTest"].runtimeClasspath
    useJUnitPlatform()

    filter {
        excludeTestsMatching("com.pintailconsultingllc.cdcdebezium.RunFailureRecoveryTest")
        excludeTestsMatching("com.pintailconsultingllc.cdcdebezium.RunObservabilityTest")
    }

    systemProperty("cucumber.junit-platform.naming-strategy", "long")
}

tasks.register<Test>("observabilityTest") {
    description = "Runs observability infrastructure acceptance tests (requires Docker services running)."
    group = "verification"
    testClassesDirs = sourceSets["acceptanceTest"].output.classesDirs
    classpath = sourceSets["acceptanceTest"].runtimeClasspath
    useJUnitPlatform()

    filter {
        includeTestsMatching("com.pintailconsultingllc.cdcdebezium.RunObservabilityTest")
    }

    systemProperty("cucumber.junit-platform.naming-strategy", "long")
}

tasks.register<Test>("failureRecoveryTest") {
    description = "Runs failure and recovery acceptance tests (requires full Docker infrastructure running)."
    group = "verification"
    testClassesDirs = sourceSets["acceptanceTest"].output.classesDirs
    classpath = sourceSets["acceptanceTest"].runtimeClasspath
    useJUnitPlatform()

    filter {
        includeTestsMatching("com.pintailconsultingllc.cdcdebezium.RunFailureRecoveryTest")
    }

    systemProperty("cucumber.junit-platform.naming-strategy", "long")
}

tasks.named("check") {
    dependsOn("acceptanceTest")
}

tasks.named<Copy>("processAcceptanceTestResources") {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
