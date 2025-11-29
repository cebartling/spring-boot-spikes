package com.pintailconsultingllc.cqrsspike.product.command.validation

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Unit tests for BusinessRulesConfig.
 *
 * AC9 Requirements:
 * - Product name is required and between 1-255 characters
 * - Product SKU is required, unique, and follows defined format (alphanumeric, 3-50 chars)
 * - Product price must be a positive integer (cents)
 * - Product description is optional but limited to 5000 characters
 * - Products in ACTIVE status require confirmation for price changes over 20%
 *
 * These tests verify that the default configuration values match AC9 requirements.
 */
@DisplayName("BusinessRulesConfig - AC9 Default Values")
class BusinessRulesConfigTest {

    @Test
    @DisplayName("should have default max name length of 255")
    fun shouldHaveDefaultMaxNameLength() {
        val config = BusinessRulesConfig()
        assertEquals(255, config.maxNameLength)
    }

    @Test
    @DisplayName("should have default min name length of 1")
    fun shouldHaveDefaultMinNameLength() {
        val config = BusinessRulesConfig()
        assertEquals(1, config.minNameLength)
    }

    @Test
    @DisplayName("should have default max SKU length of 50")
    fun shouldHaveDefaultMaxSkuLength() {
        val config = BusinessRulesConfig()
        assertEquals(50, config.maxSkuLength)
    }

    @Test
    @DisplayName("should have default min SKU length of 3")
    fun shouldHaveDefaultMinSkuLength() {
        val config = BusinessRulesConfig()
        assertEquals(3, config.minSkuLength)
    }

    @Test
    @DisplayName("should have default max description length of 5000")
    fun shouldHaveDefaultMaxDescriptionLength() {
        val config = BusinessRulesConfig()
        assertEquals(5000, config.maxDescriptionLength)
    }

    @Test
    @DisplayName("should have default price change threshold of 20%")
    fun shouldHaveDefaultPriceChangeThreshold() {
        val config = BusinessRulesConfig()
        assertEquals(20.0, config.priceChangeThresholdPercent)
    }

    @Test
    @DisplayName("should have default SKU pattern for alphanumeric with hyphens")
    fun shouldHaveDefaultSkuPattern() {
        val config = BusinessRulesConfig()
        // Pattern validates characters only; length is validated separately via minSkuLength/maxSkuLength
        assertEquals("^[A-Za-z0-9\\-]+$", config.skuPattern)
    }

    @Test
    @DisplayName("should have default max reason length of 500")
    fun shouldHaveDefaultMaxReasonLength() {
        val config = BusinessRulesConfig()
        assertEquals(500, config.maxReasonLength)
    }

    @Test
    @DisplayName("should have default max deletedBy length of 255")
    fun shouldHaveDefaultMaxDeletedByLength() {
        val config = BusinessRulesConfig()
        assertEquals(255, config.maxDeletedByLength)
    }

    @Test
    @DisplayName("should allow configuration values to be overridden")
    fun shouldAllowConfigurationOverride() {
        val config = BusinessRulesConfig().apply {
            maxNameLength = 100
            minNameLength = 5
            maxSkuLength = 30
            minSkuLength = 5
            maxDescriptionLength = 2000
            priceChangeThresholdPercent = 10.0
            skuPattern = "^[A-Z0-9]{5,30}$"
            maxReasonLength = 250
            maxDeletedByLength = 100
        }

        assertEquals(100, config.maxNameLength)
        assertEquals(5, config.minNameLength)
        assertEquals(30, config.maxSkuLength)
        assertEquals(5, config.minSkuLength)
        assertEquals(2000, config.maxDescriptionLength)
        assertEquals(10.0, config.priceChangeThresholdPercent)
        assertEquals("^[A-Z0-9]{5,30}$", config.skuPattern)
        assertEquals(250, config.maxReasonLength)
        assertEquals(100, config.maxDeletedByLength)
    }

    @Test
    @DisplayName("SKU pattern should match valid SKU formats")
    fun skuPatternShouldMatchValidFormats() {
        val config = BusinessRulesConfig()
        val pattern = Regex(config.skuPattern)

        // Valid SKUs
        assert(pattern.matches("ABC"))
        assert(pattern.matches("PROD-001"))
        assert(pattern.matches("PRODUCT123"))
        assert(pattern.matches("A-B-C-123"))
        assert(pattern.matches("a".repeat(50)))
    }

    @Test
    @DisplayName("SKU pattern should reject invalid SKU formats")
    fun skuPatternShouldRejectInvalidFormats() {
        val config = BusinessRulesConfig()
        val pattern = Regex(config.skuPattern)

        // Length validation is done separately via minSkuLength/maxSkuLength,
        // so pattern only validates character format

        // Invalid characters should be rejected by pattern
        assert(!pattern.matches("PROD@001"))
        assert(!pattern.matches("PROD 001"))
        assert(!pattern.matches("PROD#001"))
        assert(!pattern.matches("PROD.001"))

        // Empty string should not match
        assert(!pattern.matches(""))
    }
}
