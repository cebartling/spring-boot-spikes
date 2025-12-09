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

	// R2DBC PostgreSQL driver for reactive database access
	implementation("org.postgresql:r2dbc-postgresql")

	// OpenTelemetry (Spring Boot 4.0 native support)
	implementation("org.springframework.boot:spring-boot-starter-opentelemetry")

	// AspectJ for @Observed annotation support
	implementation("org.springframework.boot:spring-boot-starter-aspectj")

	developmentOnly("org.springframework.boot:spring-boot-devtools")
	testImplementation("org.springframework.boot:spring-boot-starter-webclient-test")
	testImplementation("io.projectreactor:reactor-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
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
	useJUnitPlatform()
}
