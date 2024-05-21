/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits


internal interface FrequencyChecker {
    fun isOverLimit(): Boolean
    fun checkAndIncrement(): Boolean
}
