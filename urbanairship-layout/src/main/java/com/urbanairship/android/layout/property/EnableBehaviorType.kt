/*
 Copyright Airship and Contributors
 */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException

internal enum class EnableBehaviorType(private val value: String) {
    FORM_VALIDATION("form_validation"),
    PAGER_NEXT("pager_next"),
    PAGER_PREVIOUS("pager_previous"),
    FORM_SUBMISSION("form_submission");

    override fun toString(): String {
        return name.lowercase()
    }

    companion object {
        @Throws(JsonException::class)
        fun from(value: String): EnableBehaviorType {
            for (type in entries) {
                if (type.value == value.lowercase()) {
                    return type
                }
            }
            throw JsonException("Unknown EnableBehaviorType value: $value")
        }
    }
}

internal val List<EnableBehaviorType>?.hasPagerBehaviors
    get() = this?.any {
        it == EnableBehaviorType.PAGER_NEXT || it == EnableBehaviorType.PAGER_PREVIOUS
    } ?: false

internal val List<EnableBehaviorType>?.hasFormBehaviors
    get() = this?.any {
        it == EnableBehaviorType.FORM_VALIDATION || it == EnableBehaviorType.FORM_SUBMISSION
    } ?: false
