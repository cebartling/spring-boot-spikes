package com.pintailconsultingllc.cqrsspike.product.command.validation

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Externalized configuration for product business rules.
 *
 * Implements AC9 business rule constants that can be overridden in application.yml:
 * ```yaml
 * product:
 *   rules:
 *     max-name-length: 255
 *     price-change-threshold-percent: 20.0
 * ```
 *
 * AC9 Requirements:
 * - Product name is required and between 1-255 characters
 * - Product SKU is required, unique, and follows defined format (alphanumeric, 3-50 chars)
 * - Product price must be a positive integer (cents)
 * - Product description is optional but limited to 5000 characters
 * - Products in ACTIVE status require confirmation for price changes over 20%
 */
@Configuration
@ConfigurationProperties(prefix = "product.rules")
class BusinessRulesConfig {
    /** Maximum length for product name (AC9: 255 characters) */
    var maxNameLength: Int = 255

    /** Minimum length for product name (AC9: 1 character) */
    var minNameLength: Int = 1

    /** Maximum length for product SKU (AC9: 50 characters) */
    var maxSkuLength: Int = 50

    /** Minimum length for product SKU (AC9: 3 characters) */
    var minSkuLength: Int = 3

    /** Maximum length for product description (AC9: 5000 characters) */
    var maxDescriptionLength: Int = 5000

    /** Price change threshold for ACTIVE products requiring confirmation (AC9: 20%) */
    var priceChangeThresholdPercent: Double = 20.0

    /** SKU pattern: alphanumeric and hyphens (AC9 specifies alphanumeric, hyphens added for practicality; length is validated separately) */
    var skuPattern: String = "^[A-Za-z0-9\\-]+$"

    /** Maximum length for discontinue reason */
    var maxReasonLength: Int = 500

    /** Maximum length for deletedBy field */
    var maxDeletedByLength: Int = 255
}
