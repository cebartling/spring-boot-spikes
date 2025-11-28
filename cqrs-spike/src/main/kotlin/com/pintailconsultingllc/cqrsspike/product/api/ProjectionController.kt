package com.pintailconsultingllc.cqrsspike.product.api

import com.pintailconsultingllc.cqrsspike.product.query.projection.ProjectionRunner
import com.pintailconsultingllc.cqrsspike.product.query.projection.ProjectionStatus
import com.pintailconsultingllc.cqrsspike.product.query.projection.RebuildResult
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * REST API for managing projections.
 */
@RestController
@RequestMapping("/api/admin/projections")
class ProjectionController(
    private val runner: ProjectionRunner
) {

    /**
     * Get the current status of the product projection.
     */
    @GetMapping("/product/status")
    fun getStatus(): Mono<ResponseEntity<ProjectionStatus>> {
        return runner.getStatus()
            .map { ResponseEntity.ok(it) }
    }

    /**
     * Start the projection runner.
     */
    @PostMapping("/product/start")
    fun start(): Mono<ResponseEntity<Map<String, String>>> {
        return runner.start()
            .thenReturn(ResponseEntity.ok(mapOf("status" to "started")))
            .onErrorResume { error ->
                Mono.just(
                    ResponseEntity.internalServerError()
                        .body(mapOf("status" to "error", "message" to (error.message ?: "Unknown error")))
                )
            }
    }

    /**
     * Stop the projection runner.
     */
    @PostMapping("/product/stop")
    fun stop(): Mono<ResponseEntity<Map<String, String>>> {
        return runner.stop()
            .thenReturn(ResponseEntity.ok(mapOf("status" to "stopped")))
    }

    /**
     * Rebuild the projection from scratch.
     * WARNING: This will replay all events.
     */
    @PostMapping("/product/rebuild")
    fun rebuild(): Mono<ResponseEntity<RebuildResult>> {
        return runner.rebuild()
            .map { result ->
                if (result.success) {
                    ResponseEntity.ok(result)
                } else {
                    ResponseEntity.internalServerError().body(result)
                }
            }
    }
}
