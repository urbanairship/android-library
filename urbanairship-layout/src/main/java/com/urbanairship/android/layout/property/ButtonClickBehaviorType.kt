/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList

internal enum class ButtonClickBehaviorType(
    private val value: String
) {
    // When a button is tapped, behaviors will be run in the order they are declared here.
    // Take care when adding or removing types--form submit needs to occur before dismiss or cancel.
    FORM_SUBMIT("form_submit"),
    PAGER_NEXT("pager_next"),
    PAGER_PREVIOUS("pager_previous"),
    DISMISS("dismiss"),
    CANCEL("cancel");

    override fun toString() = name.lowercase()

    companion object {
        @Throws(JsonException::class)
        fun from(value: String): ButtonClickBehaviorType {
            for (type in values()) {
                if (type.value == value.lowercase()) {
                    return type
                }
            }
            throw JsonException("Unknown ButtonClickBehaviorType value: $value")
        }

        @Throws(JsonException::class)
        fun fromList(json: JsonList): List<ButtonClickBehaviorType> =
            if (json.isEmpty) {
                emptyList()
            } else {
                json.map { from(it.optString()) }.sorted()
            }
    }
}

internal val List<ButtonClickBehaviorType>.hasFormSubmit: Boolean
    get() = contains(ButtonClickBehaviorType.FORM_SUBMIT)

internal val List<ButtonClickBehaviorType>.hasDismiss: Boolean
    get() = contains(ButtonClickBehaviorType.DISMISS)

internal val List<ButtonClickBehaviorType>.hasCancel: Boolean
    get() = contains(ButtonClickBehaviorType.CANCEL)

internal val List<ButtonClickBehaviorType>.hasCancelOrDismiss: Boolean
    get() = hasCancel || hasDismiss

internal val List<ButtonClickBehaviorType>.hasPagerNext: Boolean
    get() = contains(ButtonClickBehaviorType.PAGER_NEXT)

internal val List<ButtonClickBehaviorType>.hasPagerPrevious: Boolean
    get() = contains(ButtonClickBehaviorType.PAGER_PREVIOUS)
