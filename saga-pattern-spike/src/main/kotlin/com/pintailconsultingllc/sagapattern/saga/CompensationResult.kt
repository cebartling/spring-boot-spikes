package com.pintailconsultingllc.sagapattern.saga

/**
 * Represents the result of a compensation (rollback) operation.
 *
 * @property success Whether the compensation executed successfully
 * @property message Descriptive message about the compensation outcome
 */
data class CompensationResult(
    val success: Boolean,
    val message: String? = null
) {
    companion object {
        /**
         * Create a successful compensation result.
         */
        fun success(message: String? = null): CompensationResult = CompensationResult(
            success = true,
            message = message
        )

        /**
         * Create a failed compensation result.
         */
        fun failure(message: String): CompensationResult = CompensationResult(
            success = false,
            message = message
        )
    }
}
