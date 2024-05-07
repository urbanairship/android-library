package com.urbanairship.automation.limits

import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface FrequencyChecker {
    public fun isOverLimit(): Boolean
    public fun checkAndIncrement(): Boolean
}
