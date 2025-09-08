/* Copyright Airship and Contributors */
package com.urbanairship.json.matchers

import com.urbanairship.json.JsonValue
import com.urbanairship.json.ValueMatcher
import com.urbanairship.json.jsonMapOf
import com.google.android.gms.common.internal.Objects

/**
 * Value presence matcher.
 *
 * @hide
 */
internal class PresenceMatcher(
    private val isPresent: Boolean
) : ValueMatcher() {

    override fun toJsonValue(): JsonValue = jsonMapOf(IS_PRESENT_VALUE_KEY to isPresent).toJsonValue()

    override fun apply(value: JsonValue, ignoreCase: Boolean): Boolean {
        return if (isPresent) {
            !value.isNull
        } else {
            value.isNull
        }
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as PresenceMatcher

        return isPresent == that.isPresent
    }

    override fun hashCode(): Int {
        return Objects.hashCode(isPresent)
    }

    companion object {
        const val IS_PRESENT_VALUE_KEY: String = "is_present"
    }
}
