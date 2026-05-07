/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.android.layout.info.Identifiable
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField

internal sealed class Outcome : Identifiable {
    abstract val type: Type

    internal enum class Type(val value: String) {
        AIRSHIP_ACTION("airship_action"),
        DISMISS("dismiss"),
        PAGER_PLAYBACK("pager_playback"),
        PAGER_JUMP_NAVIGATION("pager_jump_navigation"),
        PAGER_STEP_NAVIGATION("pager_step_navigation"),
        MEDIA_PLAYBACK("media_playback"),
        MEDIA_AUDIO("media_audio"),
        STATE_ACTION("state_action"),
        FORM("form"),
        ASYNC_VIEW_RETRY("async_view_retry");

        companion object {
            fun from(value: JsonValue): Type {
                val content = value.requireString()
                return entries.firstOrNull { it.value == content }
                    ?: throw JsonException("Unknown OutcomeType: '$value'")
            }
        }
    }

    data class AirshipAction(
        override val identifier: String,
        val actions: Map<String, JsonValue>
    ) : Outcome() {
        override val type = Type.AIRSHIP_ACTION

        companion object {
            @Throws(JsonException::class)
            fun from(value: JsonValue): AirshipAction {
                val content = value.requireMap()
                return AirshipAction(
                    identifier = content.requireField("identifier"),
                    actions = content.requireField<JsonMap>("actions").map
                )
            }
        }
    }

    data class Dismiss(
        override val identifier: String,
        val cancel: Boolean = false
    ) : Outcome() {
        override val type = Type.DISMISS

        companion object {
            @Throws(JsonException::class)
            fun from(value: JsonValue): Dismiss {
                val content = value.requireMap()
                return Dismiss(
                    identifier = content.requireField("identifier"),
                    cancel = content.optionalField("cancel") ?: false
                )
            }
        }
    }

    data class PagerStepNavigation(
        override val identifier: String,
        val direction: Direction,
        val boundaryBehavior: BoundaryBehavior = BoundaryBehavior.IGNORE
    ) : Outcome() {
        override val type = Type.PAGER_STEP_NAVIGATION

        enum class Direction(val value: String) {
            NEXT("next"),
            PREVIOUS("previous");

            companion object {
                @Throws(JsonException::class)
                fun from(value: JsonValue): Direction {
                    val content = value.requireString()
                    return entries.firstOrNull { it.value == content }
                        ?: throw JsonException("Unknown PagerStepNavigation direction: '$value'")
                }
            }
        }

        enum class BoundaryBehavior(val value: String) {
            IGNORE("ignore"),
            DISMISS("dismiss"),
            WRAP("wrap");

            companion object {
                @Throws(JsonException::class)
                fun from(value: JsonValue): BoundaryBehavior {
                    val content = value.requireString()
                    return entries.firstOrNull { it.value == content }
                        ?: throw JsonException("Unknown boundary_behavior: '$value'")
                }
            }
        }

        companion object {
            @Throws(JsonException::class)
            fun from(value: JsonValue): PagerStepNavigation {
                val content = value.requireMap()
                return PagerStepNavigation(
                    identifier = content.requireField("identifier"),
                    direction = Direction.from(content.require("direction")),
                    boundaryBehavior = content["boundary_behavior"]
                        ?.let(BoundaryBehavior::from)
                        ?: BoundaryBehavior.IGNORE
                )
            }
        }
    }

    data class PagerJumpNavigation(
        override val identifier: String,
        val page: Page
    ) : Outcome() {
        override val type = Type.PAGER_JUMP_NAVIGATION

        enum class Page(val value: String) {
            START("start"),
            END("end");

            companion object {
                @Throws(JsonException::class)
                fun from(value: JsonValue): Page {
                    val content = value.requireString()
                    return entries.firstOrNull { it.value == content }
                        ?: throw JsonException("Unknown PagerJumpNavigation page: '$value'")
                }
            }
        }

        companion object {
            @Throws(JsonException::class)
            fun from(value: JsonValue): PagerJumpNavigation {
                val content = value.requireMap()
                return PagerJumpNavigation(
                    identifier = content.requireField("identifier"),
                    page = Page.from(content.require("page"))
                )
            }
        }
    }

    data class PagerPlayback(
        override val identifier: String,
        val command: Command
    ) : Outcome() {
        override val type = Type.PAGER_PLAYBACK

        enum class Command(val value: String) {
            PAUSE("pause"),
            RESUME("resume"),
            TOGGLE("toggle");

            companion object {
                @Throws(JsonException::class)
                fun from(value: JsonValue): Command {
                    val content = value.requireString()
                    return entries.firstOrNull { it.value == content }
                        ?: throw JsonException("Unknown PagerPlayback command: '$value'")
                }
            }
        }

        companion object {
            @Throws(JsonException::class)
            fun from(value: JsonValue): PagerPlayback {
                val content = value.requireMap()
                return PagerPlayback(
                    identifier = content.requireField("identifier"),
                    command = Command.from(content.require("command"))
                )
            }
        }
    }

    data class MediaPlayback(
        override val identifier: String,
        val command: Command
    ) : Outcome() {
        override val type = Type.MEDIA_PLAYBACK

        enum class Command(val value: String) {
            PLAY("play"),
            PAUSE("pause"),
            TOGGLE("toggle");

            companion object {
                @Throws(JsonException::class)
                fun from(value: JsonValue): Command {
                    val content = value.requireString()
                    return entries.firstOrNull { it.value == content }
                        ?: throw JsonException("Unknown MediaPlayback command: '$value'")
                }
            }
        }

        companion object {
            @Throws(JsonException::class)
            fun from(value: JsonValue): MediaPlayback {
                val content = value.requireMap()
                return MediaPlayback(
                    identifier = content.requireField("identifier"),
                    command = Command.from(content.require("command"))
                )
            }
        }
    }

    data class MediaAudio(
        override val identifier: String,
        val command: Command
    ) : Outcome() {
        override val type = Type.MEDIA_AUDIO

        enum class Command(val value: String) {
            MUTE("mute"),
            UNMUTE("unmute"),
            TOGGLE("toggle");

            companion object {
                @Throws(JsonException::class)
                fun from(value: JsonValue): Command {
                    val content = value.requireString()
                    return entries.firstOrNull { it.value == content }
                        ?: throw JsonException("Unknown MediaAudio command: '$value'")
                }
            }
        }

        companion object {
            @Throws(JsonException::class)
            fun from(value: JsonValue): MediaAudio {
                val content = value.requireMap()
                return MediaAudio(
                    identifier = content.requireField("identifier"),
                    command = Command.from(content.require("command"))
                )
            }
        }
    }

    data class SetStateAction(
        override val identifier: String,
        val action: StateAction
    ) : Outcome() {
        override val type = Type.STATE_ACTION

        companion object {
            @Throws(JsonException::class)
            fun from(value: JsonValue): SetStateAction {
                val content = value.requireMap()
                return SetStateAction(
                    identifier = content.requireField("identifier"),
                    action = StateAction.fromJson(content.requireField<JsonMap>("action"))
                )
            }
        }
    }

    data class Form(
        override val identifier: String,
        val command: Command
    ) : Outcome() {

        override val type = Type.FORM

        enum class Command(val value: String) { SUBMIT("submit"), VALIDATE("validate");

            companion object {

                @Throws(JsonException::class)
                fun from(value: JsonValue): Command {
                    val content = value.requireString()
                    return entries.firstOrNull { it.value == content }
                        ?: throw JsonException("Unknown Form command: '$value'")
                }
            }
        }

        companion object {
            @Throws(JsonException::class)
            fun from(value: JsonValue): Form {
                val content = value.requireMap()
                return Form(
                    identifier = content.requireField("identifier"),
                    command = Command.from(content.require("command"))
                )
            }
        }

    }

    data class AsyncViewRetry(
        override val identifier: String,
    ) : Outcome() {
        override val type = Type.ASYNC_VIEW_RETRY

        companion object {
            @Throws(JsonException::class)
            fun from(value: JsonValue): AsyncViewRetry {
                val content = value.requireMap()
                return AsyncViewRetry(
                    identifier = content.requireField("identifier")
                )
            }
        }
    }

    companion object {
        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): Outcome {
            val content = value.requireMap()
            val type = Type.from(content.require("type"))

            return when (type) {
                Type.AIRSHIP_ACTION -> AirshipAction.from(value)
                Type.DISMISS -> Dismiss.from(value)
                Type.PAGER_STEP_NAVIGATION -> PagerStepNavigation.from(value)
                Type.PAGER_JUMP_NAVIGATION -> PagerJumpNavigation.from(value)
                Type.PAGER_PLAYBACK -> PagerPlayback.from(value)
                Type.MEDIA_PLAYBACK -> MediaPlayback.from(value)
                Type.MEDIA_AUDIO -> MediaAudio.from(value)
                Type.STATE_ACTION -> SetStateAction.from(value)
                Type.FORM -> Form.from(value)
                Type.ASYNC_VIEW_RETRY -> AsyncViewRetry.from(value)
            }
        }

        @Throws(JsonException::class)
        fun fromList(json: JsonList): List<Outcome> = json.map { fromJson(it) }
    }
}

internal object OutcomeResolver {
    fun resolve(
        outcomes: List<Outcome>? = null,
        stateActions: List<StateAction>? = null,
        behaviors: List<ButtonClickBehaviorType>? = null,
        actions: Map<String, JsonValue>? = null):
            List<Outcome> {

        if (outcomes != null) return outcomes
        return buildList {
            stateActions?.forEach { add(Outcome.SetStateAction(stateActionId(it), it)) }
            behaviors?.forEach { add(it.toOutcome()) }
            if (!actions.isNullOrEmpty()) {
                add(Outcome.AirshipAction("actions_payload", actions))
            }
        }
    }

    internal fun stateActionId(action: StateAction): String = when (action) {
        is StateAction.ClearState -> "state_action_clear"
        is StateAction.SetState -> "state_action_set_${action.key}"
        is StateAction.SetFormValue -> "state_action_set_form_value_${action.key}"
    }
}

internal fun ButtonClickBehaviorType.toOutcome(): Outcome {
    val identifier = "behavior_${name.lowercase()}"
    return when (this) {
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
        ButtonClickBehaviorType.ASYNC_VIEW_RETRY -> Outcome.AsyncViewRetry(
            identifier = identifier
        )
    }
}
