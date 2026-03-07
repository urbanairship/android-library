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
    FORM_VALIDATE("form_validate"),
    PAGER_NEXT("pager_next"),
    PAGER_PREVIOUS("pager_previous"),
    /** Advances to the next page if there is one, otherwise dismisses the in-app. */
    PAGER_NEXT_OR_DISMISS("pager_next_or_dismiss"),
    /** Advances to the next page if there is one, otherwise loop back to 1st page. */
    PAGER_NEXT_OR_FIRST("pager_next_or_first"),
    PAGER_PAUSE("pager_pause"),
    PAGER_RESUME("pager_resume"),
    PAGER_PAUSE_TOGGLE("pager_toggle_pause"),
    VIDEO_PLAY("video_play"),
    VIDEO_PAUSE("video_pause"),
    VIDEO_TOGGLE_PLAY("video_toggle_play"),
    VIDEO_MUTE("video_mute"),
    VIDEO_UNMUTE("video_unmute"),
    VIDEO_TOGGLE_MUTE("video_toggle_mute"),
    DISMISS("dismiss"),
    CANCEL("cancel");

    override fun toString() = name.lowercase()

    companion object {
        @Throws(JsonException::class)
        fun from(value: String): ButtonClickBehaviorType {
            for (type in entries) {
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
    ButtonClickBehaviorType.PAGER_RESUME,
    ButtonClickBehaviorType.PAGER_PAUSE_TOGGLE
)

internal val List<ButtonClickBehaviorType>.hasStoryNavigationBehavior: Boolean
    get() = any { storyNavigationBehaviors.contains(it) }

internal val List<ButtonClickBehaviorType>.hasPagerPause: Boolean
    get() = contains(ButtonClickBehaviorType.PAGER_PAUSE)

internal val List<ButtonClickBehaviorType>.hasPagerResume: Boolean
    get() = contains(ButtonClickBehaviorType.PAGER_RESUME)

internal val List<ButtonClickBehaviorType>.hasFormSubmit: Boolean
    get() = contains(ButtonClickBehaviorType.FORM_SUBMIT)

internal val List<ButtonClickBehaviorType>.hasFormValidate: Boolean
    get() = contains(ButtonClickBehaviorType.FORM_VALIDATE)

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

internal val List<ButtonClickBehaviorType>.hasPagerPauseToggle: Boolean
    get() = contains(ButtonClickBehaviorType.PAGER_PAUSE_TOGGLE)

private val videoBehaviors = listOf(
    ButtonClickBehaviorType.VIDEO_PLAY,
    ButtonClickBehaviorType.VIDEO_PAUSE,
    ButtonClickBehaviorType.VIDEO_TOGGLE_PLAY,
    ButtonClickBehaviorType.VIDEO_MUTE,
    ButtonClickBehaviorType.VIDEO_UNMUTE,
    ButtonClickBehaviorType.VIDEO_TOGGLE_MUTE
)

internal val List<ButtonClickBehaviorType>.hasVideoBehaviors: Boolean
    get() = any { videoBehaviors.contains(it) }

internal val List<ButtonClickBehaviorType>.hasVideoPlay: Boolean
    get() = contains(ButtonClickBehaviorType.VIDEO_PLAY)

internal val List<ButtonClickBehaviorType>.hasVideoPause: Boolean
    get() = contains(ButtonClickBehaviorType.VIDEO_PAUSE)

internal val List<ButtonClickBehaviorType>.hasVideoPlayToggle: Boolean
    get() = contains(ButtonClickBehaviorType.VIDEO_TOGGLE_PLAY)

internal val List<ButtonClickBehaviorType>.hasVideoMute: Boolean
    get() = contains(ButtonClickBehaviorType.VIDEO_MUTE)

internal val List<ButtonClickBehaviorType>.hasVideoUnmute: Boolean
    get() = contains(ButtonClickBehaviorType.VIDEO_UNMUTE)

internal val List<ButtonClickBehaviorType>.hasVideoMuteToggle: Boolean
    get() = contains(ButtonClickBehaviorType.VIDEO_TOGGLE_MUTE)
