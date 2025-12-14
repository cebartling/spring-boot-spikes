plugins {
	kotlin("jvm") version "2.2.21"
	kotlin("plugin.spring") version "2.2.21"
	id("org.springframework.boot") version "4.0.0"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.pintailconsultingllc"
version = "0.0.1-SNAPSHOT"
description = "Spring Boot saga pattern spike"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(24)
	}
}

repositories {
	mavenCentral()
}

extra["springCloudVersion"] = "2025.1.0"
val cucumberVersion = "7.20.1"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-webclient")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
	implementation("tools.jackson.module:jackson-module-kotlin")

	// Spring Cloud Vault for secret management
	implementation("org.springframework.cloud:spring-cloud-starter-vault-config")
	implementation("org.springframework.cloud:spring-cloud-vault-config-databases")

	// R2DBC for reactive database access
	implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
	implementation("org.postgresql:r2dbc-postgresql")

	// OpenTelemetry (Spring Boot 4.0 native support)
	implementation("org.springframework.boot:spring-boot-starter-opentelemetry")

	// AspectJ for @Observed annotation support
	implementation("org.springframework.boot:spring-boot-starter-aspectj")

	// Prometheus metrics registry for /actuator/prometheus endpoint
	implementation("io.micrometer:micrometer-registry-prometheus")

	// Loki4j for log aggregation
	implementation("com.github.loki4j:loki-logback-appender:1.5.2")

	developmentOnly("org.springframework.boot:spring-boot-devtools")
	testImplementation("org.springframework.boot:spring-boot-starter-webclient-test")
	testImplementation("io.projectreactor:reactor-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
	testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	// Cucumber for acceptance testing
	testImplementation("io.cucumber:cucumber-java:$cucumberVersion")
	testImplementation("io.cucumber:cucumber-junit-platform-engine:$cucumberVersion")
	testImplementation("io.cucumber:cucumber-spring:$cucumberVersion")
	testImplementation("org.junit.platform:junit-platform-suite")
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
	useJUnitPlatform {
		// Filter tests by tag using system property
		// Usage: ./gradlew test -DincludeTags=unit
		//        ./gradlew test -DincludeTags=integration
		//        ./gradlew test -DexcludeTags=integration
		System.getProperty("includeTags")?.let { includeTags(*it.split(",").toTypedArray()) }
		System.getProperty("excludeTags")?.let { excludeTags(*it.split(",").toTypedArray()) }
	}
}

// Task to run only unit tests (excludes integration and acceptance tests)
tasks.register<Test>("unitTest") {
	description = "Runs unit tests only"
	group = "verification"
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	useJUnitPlatform {
		includeTags("unit")
	}
	// Exclude Cucumber test runner from unit tests
	exclude("**/CucumberTestRunner*")
}

// Task to run only integration tests
tasks.register<Test>("integrationTest") {
	description = "Runs integration tests only"
	group = "verification"
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	useJUnitPlatform {
		includeTags("integration")
	}
	// Show standard output/error for integration tests
	// This ensures Docker infrastructure messages are visible
	testLogging {
		showStandardStreams = true
		events("skipped", "failed")
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
	}
}

// Task to run only acceptance tests (Cucumber)
tasks.register<Test>("acceptanceTest") {
	description = "Runs acceptance tests only (Cucumber)"
	group = "verification"
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	useJUnitPlatform()
	// Include only the Cucumber test runner
	include("**/CucumberTestRunner*")
	// Show standard output/error for acceptance tests
	// This ensures Docker infrastructure messages are visible
	testLogging {
		showStandardStreams = true
		events("skipped", "failed")
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
	}
}
