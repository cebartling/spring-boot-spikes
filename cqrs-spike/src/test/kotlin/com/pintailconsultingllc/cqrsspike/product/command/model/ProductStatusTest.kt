package com.pintailconsultingllc.cqrsspike.product.command.model

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("ProductStatus")
class ProductStatusTest {

    @Nested
    @DisplayName("State Transitions")
    inner class StateTransitions {

        @Test
        @DisplayName("DRAFT can transition to ACTIVE")
        fun draftCanTransitionToActive() {
            assertTrue(ProductStatus.DRAFT.canTransitionTo(ProductStatus.ACTIVE))
        }

        @Test
        @DisplayName("DRAFT can transition to DISCONTINUED")
        fun draftCanTransitionToDiscontinued() {
            assertTrue(ProductStatus.DRAFT.canTransitionTo(ProductStatus.DISCONTINUED))
        }

        @Test
        @DisplayName("DRAFT cannot transition to DRAFT")
        fun draftCannotTransitionToDraft() {
            assertFalse(ProductStatus.DRAFT.canTransitionTo(ProductStatus.DRAFT))
        }

        @Test
        @DisplayName("ACTIVE can transition to DISCONTINUED")
        fun activeCanTransitionToDiscontinued() {
            assertTrue(ProductStatus.ACTIVE.canTransitionTo(ProductStatus.DISCONTINUED))
        }

        @Test
        @DisplayName("ACTIVE cannot transition to DRAFT")
        fun activeCannotTransitionToDraft() {
            assertFalse(ProductStatus.ACTIVE.canTransitionTo(ProductStatus.DRAFT))
        }

        @Test
        @DisplayName("ACTIVE cannot transition to ACTIVE")
        fun activeCannotTransitionToActive() {
            assertFalse(ProductStatus.ACTIVE.canTransitionTo(ProductStatus.ACTIVE))
        }

        @Test
        @DisplayName("DISCONTINUED cannot transition to any status")
        fun discontinuedIsTerminal() {
            ProductStatus.entries.forEach { target ->
                assertFalse(ProductStatus.DISCONTINUED.canTransitionTo(target))
            }
        }
    }

    @Nested
    @DisplayName("Valid Transitions")
    inner class ValidTransitions {

        @Test
        @DisplayName("DRAFT has two valid transitions")
        fun draftValidTransitions() {
            val expected = setOf(ProductStatus.ACTIVE, ProductStatus.DISCONTINUED)
            assertEquals(expected, ProductStatus.DRAFT.validTransitions())
        }

        @Test
        @DisplayName("ACTIVE has one valid transition")
        fun activeValidTransitions() {
            val expected = setOf(ProductStatus.DISCONTINUED)
            assertEquals(expected, ProductStatus.ACTIVE.validTransitions())
        }

        @Test
        @DisplayName("DISCONTINUED has no valid transitions")
        fun discontinuedValidTransitions() {
            assertTrue(ProductStatus.DISCONTINUED.validTransitions().isEmpty())
        }
    }
}
