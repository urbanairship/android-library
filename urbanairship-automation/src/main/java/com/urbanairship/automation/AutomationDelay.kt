/* Copyright Airship and Contributors */

package com.urbanairship.automation

import com.urbanairship.automation.engine.triggerprocessor.TriggerExecutionType
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import java.util.Objects

/**
 * Represents the app foreground/background state.
 */
public enum class AutomationAppState(internal val json: String): JsonSerializable {
    /**
     * Type representing the foreground app state.
     */
    FOREGROUND("foreground"),
    /**
     * Type representing the background app state.
     */
    BACKGROUND("background");

    override fun toJsonValue(): JsonValue = JsonValue.wrap(json)

    internal companion object {

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): AutomationAppState {
            val content = value.requireString().lowercase()
            return entries.firstOrNull { it.json == content }
                ?: throw JsonException("Invalid AutomationAppState value $content")
        }
    }
}

/**
 * Defines conditions that might delay the execution of a schedule.
 */
public class AutomationDelay(
    /**
     * The delay in seconds.
     */
    internal val seconds: Long? = null,
    /**
     * The execution screens.
     */
    internal val screens: List<String>? = null,

    /**
     * Display window for automation
     */
    internal val executionWindow: ExecutionWindow? = null,
    /**
     * The execution region ID.
     */
    public val regionId: String? = null,
    /**
     * The execution app state.
     */
    public val appState: AutomationAppState? = null,
    /**
     * The list of cancellation triggers.
     */
    public val cancellationTriggers: List<AutomationTrigger>? = null
) : JsonSerializable {

    internal companion object {
        private const val SECONDS_KEY = "seconds"
        private const val APP_STATE_KEY = "app_state"
        private const val SCREEN_KEY = "screen"
        private const val REGION_ID_KEY = "region_id"
        private const val CANCELLATION_TRIGGERS_KEY = "cancellation_triggers"
        private const val DISPLAY_WINDOW_KEY = "display_window"

        /**
         * Parses a AutomationDelay from JSON.
         * - "seconds": Optional. The minimum time in seconds that is needed to pass before running the actions.
         * - "screen": Optional string or array of strings. Specifies the name of an app screen that the user must
         * currently be viewing before the schedule's actions are able to be executed.
         * - "app_state": Optional. Specifies the app state that is required before the schedule's actions are able
         * to execute. Either "foreground" or "background". Invalid app states will throw a JsonException.
         * - "region": Optional. Specifies the ID of a region that the device must currently be in before the
         * schedule's actions are able to be executed.
         * - "cancellation_triggers": Optional. An array of triggers. Each cancels the pending execution of
         * the actions.
         *
         * @param value The schedule delay.
         * @return The parsed ScheduleDelay.
         * @throws JsonException If the json does not produce a valid ScheduleDelay.
         */
        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): AutomationDelay {
            val content = value.requireMap()

            if (content.opt(CANCELLATION_TRIGGERS_KEY).optList().count() > AutomationSchedule.TRIGGER_LIMIT) {
                throw IllegalArgumentException("No more than ${AutomationSchedule.TRIGGER_LIMIT}  cancellation triggers allowed.");
            }

            val screens = if (content.opt(SCREEN_KEY).isString) {
                listOf(content.opt(SCREEN_KEY).optString())
            } else {
                content.get(SCREEN_KEY)?.requireList()?.map { it.requireString() }
            }

            return AutomationDelay(
                seconds = content.optionalField(SECONDS_KEY),
                appState = content.get(APP_STATE_KEY)?.let(AutomationAppState::fromJson),
                screens = screens,
                regionId = content.optionalField(REGION_ID_KEY),
                cancellationTriggers = content.get(CANCELLATION_TRIGGERS_KEY)?.requireList()?.map {
                    AutomationTrigger.fromJson(it, TriggerExecutionType.DELAY_CANCELLATION)
                },
                executionWindow = content.get(DISPLAY_WINDOW_KEY)?.let(ExecutionWindow::fromJson)
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        SECONDS_KEY to seconds,
        APP_STATE_KEY to appState,
        SCREEN_KEY to screens,
        REGION_ID_KEY to regionId,
        CANCELLATION_TRIGGERS_KEY to cancellationTriggers,
        DISPLAY_WINDOW_KEY to executionWindow
    ).toJsonValue()

    override fun toString(): String = toJsonValue().toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AutomationDelay

        if (seconds != other.seconds) return false
        if (screens != other.screens) return false
        if (regionId != other.regionId) return false
        if (appState != other.appState) return false
        if (executionWindow != other.executionWindow) return false
        return cancellationTriggers == other.cancellationTriggers
    }

    override fun hashCode(): Int {
        return Objects.hash(seconds, screens, regionId, appState, cancellationTriggers, executionWindow)
    }
}
