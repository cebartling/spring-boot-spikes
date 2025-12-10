package com.pintailconsultingllc.sagapattern.retry

/**
 * Result of checking whether a previous step result is still valid.
 */
data class StepValidityResult(
    /**
     * Whether the previous step result is still valid.
     */
    val valid: Boolean,

    /**
     * Reason why the result is invalid (if applicable).
     */
    val reason: String? = null,

    /**
     * Whether the step result can be refreshed/renewed without re-executing.
     */
    val canBeRefreshed: Boolean = false
) {
    companion object {
        /**
         * Create a valid result.
         */
        fun valid(): StepValidityResult = StepValidityResult(valid = true)

        /**
         * Create an invalid result that can be refreshed.
         */
        fun expiredButRefreshable(reason: String): StepValidityResult = StepValidityResult(
            valid = false,
            reason = reason,
            canBeRefreshed = true
        )

        /**
         * Create an invalid result that requires re-execution.
         */
        fun invalidRequiresReExecution(reason: String): StepValidityResult = StepValidityResult(
            valid = false,
            reason = reason,
            canBeRefreshed = false
        )
    }
}
