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
    /** Advances to the next page if there is one, otherwise dismisses the in-app. */
    PAGER_NEXT_OR_DISMISS("pager_next_or_dismiss"),
    /** Advances to the next page if there is one, otherwise loop back to 1st page. */
    PAGER_NEXT_OR_FIRST("pager_next_or_first"),
    PAGER_PAUSE("pager_pause"),
    PAGER_RESUME("pager_resume"),
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

private val storyNavigationBehaviors = listOf(
    ButtonClickBehaviorType.CANCEL,
    ButtonClickBehaviorType.DISMISS,
    ButtonClickBehaviorType.PAGER_NEXT,
    ButtonClickBehaviorType.PAGER_PREVIOUS,
    ButtonClickBehaviorType.PAGER_NEXT_OR_DISMISS,
    ButtonClickBehaviorType.PAGER_NEXT_OR_FIRST,
    ButtonClickBehaviorType.PAGER_PAUSE,
    ButtonClickBehaviorType.PAGER_RESUME
)

internal val List<ButtonClickBehaviorType>.hasStoryNavigationBehavior: Boolean
    get() = any { storyNavigationBehaviors.contains(it) }

internal val List<ButtonClickBehaviorType>.hasPagerPause: Boolean
    get() = contains(ButtonClickBehaviorType.PAGER_PAUSE)

internal val List<ButtonClickBehaviorType>.hasPagerResume: Boolean
    get() = contains(ButtonClickBehaviorType.PAGER_RESUME)

internal val List<ButtonClickBehaviorType>.hasFormSubmit: Boolean
    get() = contains(ButtonClickBehaviorType.FORM_SUBMIT)

internal val List<ButtonClickBehaviorType>.hasDismiss: Boolean
    get() = contains(ButtonClickBehaviorType.DISMISS)

internal val List<ButtonClickBehaviorType>.hasCancel: Boolean
    get() = contains(ButtonClickBehaviorType.CANCEL)

internal val List<ButtonClickBehaviorType>.hasCancelOrDismiss: Boolean
    get() = hasCancel || hasDismiss

private val pagerNextBehaviors = listOf(
    ButtonClickBehaviorType.PAGER_NEXT,
    ButtonClickBehaviorType.PAGER_NEXT_OR_DISMISS,
    ButtonClickBehaviorType.PAGER_NEXT_OR_FIRST
)

internal val List<ButtonClickBehaviorType>.hasPagerNext: Boolean
    get() = any { pagerNextBehaviors.contains(it) }

internal val List<ButtonClickBehaviorType>.hasPagerPrevious: Boolean
    get() = contains(ButtonClickBehaviorType.PAGER_PREVIOUS)

internal fun List<ButtonClickBehaviorType>.firstPagerNextOrNull(): ButtonClickBehaviorType? =
    firstOrNull { pagerNextBehaviors.contains(it) }
