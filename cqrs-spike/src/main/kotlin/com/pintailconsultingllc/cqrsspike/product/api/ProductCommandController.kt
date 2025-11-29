package com.pintailconsultingllc.cqrsspike.product.api

import com.pintailconsultingllc.cqrsspike.product.api.dto.ActivateProductRequest
import com.pintailconsultingllc.cqrsspike.product.api.dto.ChangePriceRequest
import com.pintailconsultingllc.cqrsspike.product.api.dto.CommandErrorResponse
import com.pintailconsultingllc.cqrsspike.product.api.dto.CommandResponse
import com.pintailconsultingllc.cqrsspike.product.api.dto.CreateProductRequest
import com.pintailconsultingllc.cqrsspike.product.api.dto.CreateProductResponse
import com.pintailconsultingllc.cqrsspike.product.api.dto.DiscontinueProductRequest
import com.pintailconsultingllc.cqrsspike.product.api.dto.ProductLinks
import com.pintailconsultingllc.cqrsspike.product.api.dto.UpdateProductRequest
import com.pintailconsultingllc.cqrsspike.product.command.handler.ProductCommandHandler
import com.pintailconsultingllc.cqrsspike.product.command.model.ActivateProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.ChangePriceCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.CommandAlreadyProcessed
import com.pintailconsultingllc.cqrsspike.product.command.model.CommandResult
import com.pintailconsultingllc.cqrsspike.product.command.model.CommandSuccess
import com.pintailconsultingllc.cqrsspike.product.command.model.CreateProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.DeleteProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.DiscontinueProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.UpdateProductCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.net.URI
import java.util.UUID

/**
 * REST controller for Product command operations.
 *
 * Provides endpoints for creating, updating, and managing products
 * through the CQRS command pipeline.
 */
@RestController
@RequestMapping("/api/products")
@Validated
@Tag(name = "Product Commands", description = "Endpoints for product command operations")
class ProductCommandController(
    private val commandHandler: ProductCommandHandler
) {
    private val logger = LoggerFactory.getLogger(ProductCommandController::class.java)

    companion object {
        const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
        const val IDEMPOTENT_REPLAYED_HEADER = "X-Idempotent-Replayed"
    }

    /**
     * Create a new product.
     */
    @PostMapping
    @Operation(
        summary = "Create a new product",
        description = "Creates a new product in DRAFT status"
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "Product created successfully",
            headers = [Header(name = "Location", description = "URL of created product")]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Validation error",
            content = [Content(schema = Schema(implementation = CommandErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "409",
            description = "SKU already exists",
            content = [Content(schema = Schema(implementation = CommandErrorResponse::class))]
        )
    )
    fun createProduct(
        @Valid @RequestBody request: CreateProductRequest,
        @RequestHeader(IDEMPOTENCY_KEY_HEADER, required = false) idempotencyKey: String?
    ): Mono<ResponseEntity<CreateProductResponse>> {
        logger.info("POST /api/products - Creating product with SKU: {}", request.sku)

        val command = CreateProductCommand(
            sku = request.sku.trim(),
            name = request.name.trim(),
            description = request.description?.trim(),
            priceCents = request.priceCents,
            idempotencyKey = idempotencyKey
        )

        return commandHandler.handle(command)
            .map { result ->
                when (result) {
                    is CommandSuccess -> {
                        val response = CreateProductResponse(
                            productId = result.productId,
                            sku = request.sku,
                            version = result.version,
                            status = result.status ?: "DRAFT",
                            links = buildProductLinks(result.productId, result.status ?: "DRAFT")
                        )
                        ResponseEntity
                            .created(URI.create("/api/products/${result.productId}"))
                            .body(response)
                    }
                    is CommandAlreadyProcessed -> {
                        val response = CreateProductResponse(
                            productId = result.productId,
                            sku = request.sku,
                            version = result.version,
                            status = result.status ?: "DRAFT"
                        )
                        ResponseEntity
                            .status(HttpStatus.CREATED)
                            .header(IDEMPOTENT_REPLAYED_HEADER, "true")
                            .body(response)
                    }
                    else -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
                }
            }
    }

    /**
     * Update an existing product.
     */
    @PutMapping("/{id}")
    @Operation(
        summary = "Update product details",
        description = "Updates name and description of an existing product"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Product updated successfully"),
        ApiResponse(
            responseCode = "400",
            description = "Validation error",
            content = [Content(schema = Schema(implementation = CommandErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "404",
            description = "Product not found",
            content = [Content(schema = Schema(implementation = CommandErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "409",
            description = "Concurrent modification",
            content = [Content(schema = Schema(implementation = CommandErrorResponse::class))]
        )
    )
    fun updateProduct(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateProductRequest,
        @RequestHeader(IDEMPOTENCY_KEY_HEADER, required = false) idempotencyKey: String?
    ): Mono<ResponseEntity<CommandResponse>> {
        logger.info("PUT /api/products/{} - Updating product", id)

        val command = UpdateProductCommand(
            productId = id,
            expectedVersion = request.expectedVersion,
            name = request.name.trim(),
            description = request.description?.trim(),
            idempotencyKey = idempotencyKey
        )

        return commandHandler.handle(command)
            .map { result -> buildCommandResponse(result, id) }
    }

    /**
     * Change product price.
     */
    @PatchMapping("/{id}/price")
    @Operation(
        summary = "Change product price",
        description = "Updates the price of a product. Large changes (>20%) for ACTIVE products require confirmation."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Price changed successfully"),
        ApiResponse(
            responseCode = "400",
            description = "Validation error",
            content = [Content(schema = Schema(implementation = CommandErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "404",
            description = "Product not found",
            content = [Content(schema = Schema(implementation = CommandErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "409",
            description = "Concurrent modification",
            content = [Content(schema = Schema(implementation = CommandErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "422",
            description = "Price change exceeds threshold without confirmation",
            content = [Content(schema = Schema(implementation = CommandErrorResponse::class))]
        )
    )
    fun changePrice(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ChangePriceRequest,
        @RequestHeader(IDEMPOTENCY_KEY_HEADER, required = false) idempotencyKey: String?
    ): Mono<ResponseEntity<CommandResponse>> {
        logger.info("PATCH /api/products/{}/price - Changing price to {} cents", id, request.newPriceCents)

        val command = ChangePriceCommand(
            productId = id,
            expectedVersion = request.expectedVersion,
            newPriceCents = request.newPriceCents,
            confirmLargeChange = request.confirmLargeChange,
            idempotencyKey = idempotencyKey
        )

        return commandHandler.handle(command)
            .map { result -> buildCommandResponse(result, id) }
    }

    /**
     * Activate a product.
     */
    @PostMapping("/{id}/activate")
    @Operation(
        summary = "Activate a product",
        description = "Transitions a DRAFT product to ACTIVE status"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Product activated successfully"),
        ApiResponse(
            responseCode = "404",
            description = "Product not found",
            content = [Content(schema = Schema(implementation = CommandErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "409",
            description = "Concurrent modification",
            content = [Content(schema = Schema(implementation = CommandErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "422",
            description = "Invalid state transition",
            content = [Content(schema = Schema(implementation = CommandErrorResponse::class))]
        )
    )
    fun activateProduct(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ActivateProductRequest,
        @RequestHeader(IDEMPOTENCY_KEY_HEADER, required = false) idempotencyKey: String?
    ): Mono<ResponseEntity<CommandResponse>> {
        logger.info("POST /api/products/{}/activate - Activating product", id)

        val command = ActivateProductCommand(
            productId = id,
            expectedVersion = request.expectedVersion,
            idempotencyKey = idempotencyKey
        )

        return commandHandler.handle(command)
            .map { result -> buildCommandResponse(result, id) }
    }

    /**
     * Discontinue a product.
     */
    @PostMapping("/{id}/discontinue")
    @Operation(
        summary = "Discontinue a product",
        description = "Transitions a product to DISCONTINUED status"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Product discontinued successfully"),
        ApiResponse(
            responseCode = "404",
            description = "Product not found",
            content = [Content(schema = Schema(implementation = CommandErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "409",
            description = "Concurrent modification",
            content = [Content(schema = Schema(implementation = CommandErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "422",
            description = "Invalid state transition",
            content = [Content(schema = Schema(implementation = CommandErrorResponse::class))]
        )
    )
    fun discontinueProduct(
        @PathVariable id: UUID,
        @Valid @RequestBody request: DiscontinueProductRequest,
        @RequestHeader(IDEMPOTENCY_KEY_HEADER, required = false) idempotencyKey: String?
    ): Mono<ResponseEntity<CommandResponse>> {
        logger.info("POST /api/products/{}/discontinue - Discontinuing product", id)

        val command = DiscontinueProductCommand(
            productId = id,
            expectedVersion = request.expectedVersion,
            reason = request.reason?.trim(),
            idempotencyKey = idempotencyKey
        )

        return commandHandler.handle(command)
            .map { result -> buildCommandResponse(result, id) }
    }

    /**
     * Delete a product (soft delete).
     */
    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete a product",
        description = "Soft-deletes a product"
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Product deleted successfully"),
        ApiResponse(
            responseCode = "400",
            description = "Missing expectedVersion parameter",
            content = [Content(schema = Schema(implementation = CommandErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "404",
            description = "Product not found",
            content = [Content(schema = Schema(implementation = CommandErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "409",
            description = "Concurrent modification",
            content = [Content(schema = Schema(implementation = CommandErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "410",
            description = "Product already deleted",
            content = [Content(schema = Schema(implementation = CommandErrorResponse::class))]
        )
    )
    fun deleteProduct(
        @PathVariable id: UUID,
        @RequestParam(required = true) expectedVersion: Long,
        @RequestParam(required = false) deletedBy: String?,
        @RequestHeader(IDEMPOTENCY_KEY_HEADER, required = false) idempotencyKey: String?
    ): Mono<ResponseEntity<Void>> {
        logger.info("DELETE /api/products/{} - Deleting product", id)

        val command = DeleteProductCommand(
            productId = id,
            expectedVersion = expectedVersion,
            deletedBy = deletedBy?.trim(),
            idempotencyKey = idempotencyKey
        )

        return commandHandler.handle(command)
            .map { result ->
                when (result) {
                    is CommandSuccess, is CommandAlreadyProcessed ->
                        ResponseEntity.noContent().build()
                    else ->
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
                }
            }
    }

    // ============ Helper Methods ============

    private fun buildCommandResponse(
        result: CommandResult,
        productId: UUID
    ): ResponseEntity<CommandResponse> {
        return when (result) {
            is CommandSuccess -> {
                val response = CommandResponse(
                    productId = result.productId,
                    version = result.version,
                    status = result.status ?: "UNKNOWN"
                )
                ResponseEntity.ok(response)
            }
            is CommandAlreadyProcessed -> {
                val response = CommandResponse(
                    productId = result.productId,
                    version = result.version,
                    status = result.status ?: "UNKNOWN"
                )
                ResponseEntity.ok()
                    .header(IDEMPOTENT_REPLAYED_HEADER, "true")
                    .body(response)
            }
            else -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    private fun buildProductLinks(productId: UUID, status: String): ProductLinks {
        val baseUrl = "/api/products/$productId"
        return ProductLinks(
            self = baseUrl,
            update = baseUrl,
            activate = if (status == "DRAFT") "$baseUrl/activate" else null,
            discontinue = if (status in listOf("DRAFT", "ACTIVE")) "$baseUrl/discontinue" else null,
            delete = baseUrl
        )
    }
}
