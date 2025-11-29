package com.pintailconsultingllc.cqrsspike.product.api.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

/**
 * Request DTO for creating a new product.
 */
data class CreateProductRequest(
    @field:NotBlank(message = "SKU is required")
    @field:Size(min = 3, max = 50, message = "SKU must be 3-50 characters")
    @field:Pattern(
        regexp = "^[A-Za-z0-9-]+$",
        message = "SKU must contain only alphanumeric characters and hyphens"
    )
    val sku: String,

    @field:NotBlank(message = "Name is required")
    @field:Size(min = 1, max = 255, message = "Name must be 1-255 characters")
    val name: String,

    @field:Size(max = 5000, message = "Description must not exceed 5000 characters")
    val description: String? = null,

    @field:Positive(message = "Price must be a positive integer (cents)")
    val priceCents: Int
)

/**
 * Request DTO for updating product details.
 */
data class UpdateProductRequest(
    @field:NotBlank(message = "Name is required")
    @field:Size(min = 1, max = 255, message = "Name must be 1-255 characters")
    val name: String,

    @field:Size(max = 5000, message = "Description must not exceed 5000 characters")
    val description: String? = null,

    @field:Min(value = 0, message = "Expected version must be non-negative")
    val expectedVersion: Long
)

/**
 * Request DTO for changing product price.
 */
data class ChangePriceRequest(
    @field:Positive(message = "Price must be a positive integer (cents)")
    val newPriceCents: Int,

    val confirmLargeChange: Boolean = false,

    @field:Min(value = 0, message = "Expected version must be non-negative")
    val expectedVersion: Long
)

/**
 * Request DTO for activating a product.
 */
data class ActivateProductRequest(
    @field:Min(value = 0, message = "Expected version must be non-negative")
    val expectedVersion: Long
)

/**
 * Request DTO for discontinuing a product.
 */
data class DiscontinueProductRequest(
    @field:Size(max = 500, message = "Reason must not exceed 500 characters")
    val reason: String? = null,

    @field:Min(value = 0, message = "Expected version must be non-negative")
    val expectedVersion: Long
)

/**
 * Request DTO for deleting a product.
 */
data class DeleteProductRequest(
    @field:Size(max = 100, message = "DeletedBy must not exceed 100 characters")
    val deletedBy: String? = null,

    @field:Min(value = 0, message = "Expected version must be non-negative")
    val expectedVersion: Long
)
