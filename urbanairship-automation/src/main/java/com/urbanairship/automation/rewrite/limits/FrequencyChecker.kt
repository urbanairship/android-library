package com.urbanairship.automation.rewrite.limits

internal interface FrequencyCheckerInterface {
    suspend fun isOverLimit(): Boolean
    suspend fun checkAndIncrement(): Boolean
}

internal class FrequencyChecker(
    private val overLimitGetter: suspend () -> Boolean,
    private val doCheckAndIncrement: suspend () -> Boolean
): FrequencyCheckerInterface {
    override suspend fun isOverLimit(): Boolean = overLimitGetter()
    override suspend fun checkAndIncrement(): Boolean = doCheckAndIncrement()
}
