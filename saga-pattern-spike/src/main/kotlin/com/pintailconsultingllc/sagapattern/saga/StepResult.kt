package com.pintailconsultingllc.sagapattern.saga

/**
 * Represents the result of executing a saga step.
 *
 * @property success Whether the step executed successfully
 * @property data Data to pass to subsequent steps
 * @property errorMessage Error details if the step failed
 * @property errorCode Machine-readable error code for categorizing failures
 */
data class StepResult(
    val success: Boolean,
    val data: Map<String, Any> = emptyMap(),
    val errorMessage: String? = null,
    val errorCode: String? = null
) {
    companion object {
        /**
         * Create a successful step result.
         */
        fun success(data: Map<String, Any> = emptyMap()): StepResult = StepResult(
            success = true,
            data = data
        )

        /**
         * Create a failed step result.
         */
        fun failure(errorMessage: String, errorCode: String? = null): StepResult = StepResult(
            success = false,
            errorMessage = errorMessage,
            errorCode = errorCode
        )
    }

    /**
     * Get a data value by key with type safety.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getData(key: String): T? = data[key] as? T
}
