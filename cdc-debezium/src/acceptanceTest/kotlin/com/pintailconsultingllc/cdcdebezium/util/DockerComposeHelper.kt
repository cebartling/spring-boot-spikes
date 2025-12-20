package com.pintailconsultingllc.cdcdebezium.util

import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class DockerComposeHelper(
    private val projectDir: String = System.getProperty("user.dir")
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun stopService(serviceName: String): Boolean {
        logger.info("Stopping Docker service: {}", serviceName)
        return executeDockerCompose("stop", serviceName)
    }

    fun startService(serviceName: String): Boolean {
        logger.info("Starting Docker service: {}", serviceName)
        return executeDockerCompose("start", serviceName)
    }

    fun isServiceHealthy(serviceName: String): Boolean {
        val result = executeDockerComposeWithOutput("ps", "--format", "json", serviceName)
        return result.contains("\"Health\": \"healthy\"") || result.contains("\"State\": \"running\"")
    }

    fun waitForServiceHealthy(
        serviceName: String,
        timeout: Duration = Duration.ofSeconds(60)
    ): Boolean {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            if (isServiceHealthy(serviceName)) {
                logger.info("Service {} is healthy", serviceName)
                return true
            }
            Thread.sleep(2000)
        }
        logger.warn("Timeout waiting for service {} to become healthy", serviceName)
        return false
    }

    fun waitForServiceUnavailable(
        serviceName: String,
        timeout: Duration = Duration.ofSeconds(30)
    ): Boolean {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            if (!isServiceHealthy(serviceName)) {
                logger.info("Service {} is unavailable", serviceName)
                return true
            }
            Thread.sleep(1000)
        }
        logger.warn("Timeout waiting for service {} to become unavailable", serviceName)
        return false
    }

    fun executePsql(sql: String): String {
        return executeDockerComposeWithOutput(
            "exec", "-T", "postgres",
            "psql", "-U", "postgres", "-t", "-c", sql
        )
    }

    private fun executeDockerCompose(vararg args: String): Boolean {
        val command = listOf("docker", "compose") + args.toList()
        return try {
            val process = ProcessBuilder(command)
                .directory(File(projectDir))
                .inheritIO()
                .start()
            process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0
        } catch (e: Exception) {
            logger.error("Error executing docker compose command: {}", e.message, e)
            false
        }
    }

    private fun executeDockerComposeWithOutput(vararg args: String): String {
        val command = listOf("docker", "compose") + args.toList()
        return try {
            val process = ProcessBuilder(command)
                .directory(File(projectDir))
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(60, TimeUnit.SECONDS)
            output
        } catch (e: Exception) {
            logger.error("Error executing docker compose command: {}", e.message, e)
            ""
        }
    }
}
