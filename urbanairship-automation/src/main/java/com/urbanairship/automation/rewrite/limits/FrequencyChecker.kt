package com.urbanairship.automation.rewrite.limits

import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface FrequencyCheckerInterface {
    public suspend fun isOverLimit(): Boolean
    public suspend fun checkAndIncrement(): Boolean
}

internal class FrequencyChecker(
    private val overLimitGetter: suspend () -> Boolean,
    private val doCheckAndIncrement: suspend () -> Boolean
): FrequencyCheckerInterface {
    override suspend fun isOverLimit(): Boolean = overLimitGetter()
    override suspend fun checkAndIncrement(): Boolean = doCheckAndIncrement()
}
