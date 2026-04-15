/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.android.layout.info.Identifiable
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalList
import com.urbanairship.json.optionalMap
import com.urbanairship.json.requireField

internal enum class OutcomeType(val value: String) {
    AIRSHIP_ACTION("airship_action"),
    DISMISS("dismiss"),
    PAGER_PLAYBACK("pager_playback"),
    PAGER_JUMP_NAVIGATION("pager_jump_navigation"),
    PAGER_STEP_NAVIGATION("pager_step_navigation"),
    MEDIA_PLAYBACK("media_playback"),
    MEDIA_AUDIO("media_audio"),
    STATE_ACTION("state_action"),
    FORM("form");

    companion object {
        fun from(value: String): OutcomeType =
            entries.firstOrNull { it.value == value }
                ?: throw JsonException("Unknown OutcomeType: '$value'")
    }
}

internal sealed class Outcome : Identifiable {
    abstract val type: OutcomeType

    data class AirshipAction(
        override val identifier: String,
        val actions: Map<String, JsonValue>
    ) : Outcome() {
        override val type = OutcomeType.AIRSHIP_ACTION
    }

    data class Dismiss(
        override val identifier: String,
        val cancel: Boolean = false
    ) : Outcome() {
        override val type = OutcomeType.DISMISS
    }

    data class PagerStepNavigation(
        override val identifier: String,
        val direction: Direction,
        val boundaryBehavior: BoundaryBehavior = BoundaryBehavior.IGNORE
    ) : Outcome() {
        override val type = OutcomeType.PAGER_STEP_NAVIGATION

        enum class Direction(val value: String) {
            NEXT("next"),
            PREVIOUS("previous");

            companion object {
                fun from(value: String): Direction =
                    entries.firstOrNull { it.value == value }
                        ?: throw JsonException("Unknown PagerStepNavigation direction: '$value'")
            }
        }

        enum class BoundaryBehavior(val value: String) {
            IGNORE("ignore"),
            DISMISS("dismiss"),
            WRAP("wrap");

            companion object {
                fun from(value: String): BoundaryBehavior =
                    entries.firstOrNull { it.value == value }
                        ?: throw JsonException("Unknown boundary_behavior: '$value'")
            }
        }
    }

    data class PagerJumpNavigation(
        override val identifier: String,
        val page: Page
    ) : Outcome() {
        override val type = OutcomeType.PAGER_JUMP_NAVIGATION

        enum class Page(val value: String) {
            START("start"),
            END("end");

            companion object {
                fun from(value: String): Page =
                    entries.firstOrNull { it.value == value }
                        ?: throw JsonException("Unknown PagerJumpNavigation page: '$value'")
            }
        }
    }

    data class PagerPlayback(
        override val identifier: String,
        val command: Command
    ) : Outcome() {
        override val type = OutcomeType.PAGER_PLAYBACK

        enum class Command(val value: String) {
            PAUSE("pause"),
            RESUME("resume"),
            TOGGLE("toggle");

            companion object {
                fun from(value: String): Command =
                    entries.firstOrNull { it.value == value }
                        ?: throw JsonException("Unknown PagerPlayback command: '$value'")
            }
        }
    }

    data class MediaPlayback(
        override val identifier: String,
        val command: Command
    ) : Outcome() {
        override val type = OutcomeType.MEDIA_PLAYBACK

        enum class Command(val value: String) {
            PLAY("play"),
            PAUSE("pause"),
            TOGGLE("toggle");

            companion object {
                fun from(value: String): Command =
                    entries.firstOrNull { it.value == value }
                        ?: throw JsonException("Unknown MediaPlayback command: '$value'")
            }
        }
    }

    data class MediaAudio(
        override val identifier: String,
        val command: Command
    ) : Outcome() {
        override val type = OutcomeType.MEDIA_AUDIO

        enum class Command(val value: String) {
            MUTE("mute"),
            UNMUTE("unmute"),
            TOGGLE("toggle");

            companion object {
                fun from(value: String): Command =
                    entries.firstOrNull { it.value == value }
                        ?: throw JsonException("Unknown MediaAudio command: '$value'")
            }
        }
    }

    data class SetStateAction(
        override val identifier: String,
        val action: StateAction
    ) : Outcome() {
        override val type = OutcomeType.STATE_ACTION
    }

    data class Form(
        override val identifier: String,
        val command: Command
    ) : Outcome() {
        override val type = OutcomeType.FORM

        enum class Command(val value: String) {
            SUBMIT("submit"),
            VALIDATE("validate");

            companion object {
                fun from(value: String): Command =
                    entries.firstOrNull { it.value == value }
                        ?: throw JsonException("Unknown Form command: '$value'")
            }
        }
    }

    /**
     * Internal-only outcome used to represent the legacy ASYNC_VIEW_RETRY behavior.
     * Not parseable from JSON — only produced by [behaviorToOutcome].
     */
    data class AsyncViewReload(
        override val identifier: String
    ) : Outcome() {
        override val type = OutcomeType.DISMISS
    }

    companion object {
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): Outcome {
            val type = OutcomeType.from(json.requireField("type"))
            val identifier: String = json.requireField("identifier")

            return when (type) {
                OutcomeType.AIRSHIP_ACTION -> AirshipAction(
                    identifier = identifier,
                    actions = json.requireField<JsonMap>("actions").map
                )

                OutcomeType.DISMISS -> Dismiss(
                    identifier = identifier,
                    cancel = json.optionalField("cancel") ?: false
                )

                OutcomeType.PAGER_STEP_NAVIGATION -> PagerStepNavigation(
                    identifier = identifier,
                    direction = PagerStepNavigation.Direction.from(json.requireField("direction")),
                    boundaryBehavior = json.optionalField<String>("boundary_behavior")
                        ?.let(PagerStepNavigation.BoundaryBehavior::from)
                        ?: PagerStepNavigation.BoundaryBehavior.IGNORE
                )

                OutcomeType.PAGER_JUMP_NAVIGATION -> PagerJumpNavigation(
                    identifier = identifier,
                    page = PagerJumpNavigation.Page.from(json.requireField("page"))
                )

                OutcomeType.PAGER_PLAYBACK -> PagerPlayback(
                    identifier = identifier,
                    command = PagerPlayback.Command.from(json.requireField("command"))
                )

                OutcomeType.MEDIA_PLAYBACK -> MediaPlayback(
                    identifier = identifier,
                    command = MediaPlayback.Command.from(json.requireField("command"))
                )

                OutcomeType.MEDIA_AUDIO -> MediaAudio(
                    identifier = identifier,
                    command = MediaAudio.Command.from(json.requireField("command"))
                )

                OutcomeType.STATE_ACTION -> SetStateAction(
                    identifier = identifier,
                    action = StateAction.fromJson(json.requireField<JsonMap>("action"))
                )

                OutcomeType.FORM -> Form(
                    identifier = identifier,
                    command = Form.Command.from(json.requireField("command"))
                )
            }
        }

        @Throws(JsonException::class)
        fun fromList(json: JsonList): List<Outcome> =
            if (json.isEmpty) emptyList() else json.map { fromJson(it.requireMap()) }
    }
}

/**
 * Holds either explicit [outcomes] (new format) or legacy fields ([stateActions], [behaviors],
 * [actions]). When [outcomes] is non-null, legacy fields are ignored.
 *
 * Call [resolve] to get a unified list of [Outcome] regardless of which format was provided.
 */
internal data class OutcomeParams(
    val outcomes: List<Outcome>? = null,
    val stateActions: List<StateAction>? = null,
    val behaviors: List<ButtonClickBehaviorType>? = null,
    val actions: Map<String, JsonValue>? = null
) {
    fun resolve(): List<Outcome> {
        if (outcomes != null) return outcomes

        val result = mutableListOf<Outcome>()

        stateActions?.forEach { action ->
            val identifier = when (action) {
                is StateAction.ClearState -> "state_action_clear"
                is StateAction.SetState -> "state_action_set_${action.key}"
                is StateAction.SetFormValue -> "state_action_set_form_value_${action.key}"
            }
            result.add(Outcome.SetStateAction(identifier = identifier, action = action))
        }

        behaviors?.forEach { behavior ->
            result.add(behaviorToOutcome(behavior))
        }

        if (!actions.isNullOrEmpty()) {
            result.add(
                Outcome.AirshipAction(
                    identifier = "actions_payload",
                    actions = actions
                )
            )
        }

        return result
    }

    val hasFormOutcome: Boolean
        get() {
            if (outcomes != null) {
                return outcomes.any { it is Outcome.Form }
            }
            return behaviors?.any {
                it == ButtonClickBehaviorType.FORM_SUBMIT || it == ButtonClickBehaviorType.FORM_VALIDATE
            } == true
        }

    val hasForwardOutcome: Boolean
        get() {
            if (outcomes != null) {
                return outcomes.any { outcome ->
                    (outcome is Outcome.PagerStepNavigation && outcome.direction == Outcome.PagerStepNavigation.Direction.NEXT) ||
                    (outcome is Outcome.PagerJumpNavigation && outcome.page == Outcome.PagerJumpNavigation.Page.END) ||
                    (outcome is Outcome.PagerPlayback && outcome.command == Outcome.PagerPlayback.Command.RESUME)
                }
            }
            val advanceBehaviors = listOf(
                ButtonClickBehaviorType.PAGER_NEXT,
                ButtonClickBehaviorType.PAGER_NEXT_OR_DISMISS,
                ButtonClickBehaviorType.PAGER_NEXT_OR_FIRST,
                ButtonClickBehaviorType.PAGER_RESUME
            )
            return behaviors?.any { advanceBehaviors.contains(it) } == true
        }

    companion object {
        val EMPTY = OutcomeParams()

        /**
         * Build from a JSON source that may have `outcomes`, `state_actions`, `behaviors`, and `actions`.
         */
        fun fromButton(json: JsonMap): OutcomeParams = OutcomeParams(
            outcomes = json.optionalList("outcomes")?.let(Outcome::fromList),
            stateActions = null,
            behaviors = json.optionalList("button_click")?.let(ButtonClickBehaviorType::fromList),
            actions = json.optionalMap("actions")?.map
        )
    }
}

private fun behaviorToOutcome(behavior: ButtonClickBehaviorType): Outcome {
    val identifier = "behavior_${behavior.name.lowercase()}"
    return when (behavior) {
        ButtonClickBehaviorType.PAGER_NEXT -> Outcome.PagerStepNavigation(
            identifier = identifier,
            direction = Outcome.PagerStepNavigation.Direction.NEXT
        )
        ButtonClickBehaviorType.PAGER_PREVIOUS -> Outcome.PagerStepNavigation(
            identifier = identifier,
            direction = Outcome.PagerStepNavigation.Direction.PREVIOUS
        )
        ButtonClickBehaviorType.PAGER_NEXT_OR_DISMISS -> Outcome.PagerStepNavigation(
            identifier = identifier,
            direction = Outcome.PagerStepNavigation.Direction.NEXT,
            boundaryBehavior = Outcome.PagerStepNavigation.BoundaryBehavior.DISMISS
        )
        ButtonClickBehaviorType.PAGER_NEXT_OR_FIRST -> Outcome.PagerStepNavigation(
            identifier = identifier,
            direction = Outcome.PagerStepNavigation.Direction.NEXT,
            boundaryBehavior = Outcome.PagerStepNavigation.BoundaryBehavior.WRAP
        )
        ButtonClickBehaviorType.PAGER_PAUSE -> Outcome.PagerPlayback(
            identifier = identifier,
            command = Outcome.PagerPlayback.Command.PAUSE
        )
        ButtonClickBehaviorType.PAGER_RESUME -> Outcome.PagerPlayback(
            identifier = identifier,
            command = Outcome.PagerPlayback.Command.RESUME
        )
        ButtonClickBehaviorType.PAGER_PAUSE_TOGGLE -> Outcome.PagerPlayback(
            identifier = identifier,
            command = Outcome.PagerPlayback.Command.TOGGLE
        )
        ButtonClickBehaviorType.DISMISS -> Outcome.Dismiss(identifier = identifier)
        ButtonClickBehaviorType.CANCEL -> Outcome.Dismiss(identifier = identifier, cancel = true)
        ButtonClickBehaviorType.VIDEO_PLAY -> Outcome.MediaPlayback(
            identifier = identifier,
            command = Outcome.MediaPlayback.Command.PLAY
        )
        ButtonClickBehaviorType.VIDEO_PAUSE -> Outcome.MediaPlayback(
            identifier = identifier,
            command = Outcome.MediaPlayback.Command.PAUSE
        )
        ButtonClickBehaviorType.VIDEO_TOGGLE_PLAY -> Outcome.MediaPlayback(
            identifier = identifier,
            command = Outcome.MediaPlayback.Command.TOGGLE
        )
        ButtonClickBehaviorType.VIDEO_MUTE -> Outcome.MediaAudio(
            identifier = identifier,
            command = Outcome.MediaAudio.Command.MUTE
        )
        ButtonClickBehaviorType.VIDEO_UNMUTE -> Outcome.MediaAudio(
            identifier = identifier,
            command = Outcome.MediaAudio.Command.UNMUTE
        )
        ButtonClickBehaviorType.VIDEO_TOGGLE_MUTE -> Outcome.MediaAudio(
            identifier = identifier,
            command = Outcome.MediaAudio.Command.TOGGLE
        )
        ButtonClickBehaviorType.FORM_SUBMIT -> Outcome.Form(
            identifier = identifier,
            command = Outcome.Form.Command.SUBMIT
        )
        ButtonClickBehaviorType.FORM_VALIDATE -> Outcome.Form(
            identifier = identifier,
            command = Outcome.Form.Command.VALIDATE
        )
        ButtonClickBehaviorType.ASYNC_VIEW_RETRY -> Outcome.AsyncViewReload(
            identifier = identifier
        )
    }
}
