/* Copyright Airship and Contributors */
package com.urbanairship.json.matchers

import com.urbanairship.json.JsonException
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

    @Throws(JsonException::class)
    override fun toJsonValue(): JsonValue = jsonMapOf(IS_PRESENT_VALUE_KEY to isPresent).toJsonValue()

    override fun apply(jsonValue: JsonValue, ignoreCase: Boolean): Boolean {
        return if (isPresent) {
            !jsonValue.isNull
        } else {
            jsonValue.isNull
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }

        val that = other as PresenceMatcher

        return isPresent == that.isPresent
    }

    override fun hashCode(): Int {
        return Objects.hashCode(isPresent)
    }

    companion object {
        const val IS_PRESENT_VALUE_KEY: String = "is_present"
    }
}
