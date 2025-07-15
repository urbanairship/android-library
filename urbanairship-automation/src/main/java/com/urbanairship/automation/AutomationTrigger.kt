/* Copyright Airship and Contributors */

package com.urbanairship.automation

import com.urbanairship.automation.engine.AutomationEvent
import com.urbanairship.automation.engine.TriggerableState
import com.urbanairship.automation.engine.triggerprocessor.MatchResult
import com.urbanairship.automation.engine.triggerprocessor.TriggerData
import com.urbanairship.automation.engine.triggerprocessor.TriggerExecutionType
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import com.urbanairship.util.UAStringUtil
import com.urbanairship.util.VersionUtils
import java.util.Objects
import java.util.UUID

/**
 * Event automation trigger types.
 */
public enum class EventAutomationTriggerType(internal val value: String) : JsonSerializable {

    /**
     * Foreground
     */
    FOREGROUND("foreground"),

    /**
     * Background
     */
    BACKGROUND("background"),

    /**
     * Screen view
     */
    SCREEN("screen"),

    /**
     * Version update
     */
    VERSION("version"),

    /**
     * App init
     */
    APP_INIT("app_init"),

    /**
     * Region enter
     */
    REGION_ENTER("region_enter"),

    /**
     * Region exit
     */
    REGION_EXIT("region_exit"),

    /**
     * Custom event count
     */
    CUSTOM_EVENT_COUNT("custom_event_count"),

    /**
     * Custom event value
     */
    CUSTOM_EVENT_VALUE("custom_event_value"),

    /**
     * Feature flag interaction
     */
    FEATURE_FLAG_INTERACTION("feature_flag_interaction"),

    /**
     * Active session
     */
    ACTIVE_SESSION("active_session"),

    /**
     * IAX display
     */
    IN_APP_DISPLAY("in_app_display"),

    /**
     * IAX resolution
     */
    IN_APP_RESOLUTION("in_app_resolution"),

    /**
     * IAX button tap
     */
    IN_APP_BUTTON_TAP("in_app_button_tap"),

    /**
     * IAX permission result
     */
    IN_APP_PERMISSION_RESULT("in_app_permission_result"),

    /**
     * IAX form display
     */
    IN_APP_FORM_DISPLAY("in_app_form_display"),

    /**
     * IAX form result
     */
    IN_APP_FORM_RESULT("in_app_form_result"),

    /**
     * IAX gesture
     */
    IN_APP_GESTURE("in_app_gesture"),

    /**
     * IAX pager completed
     */
    IN_APP_PAGER_COMPLETED("in_app_pager_completed"),

    /**
     * IAX pager summary
     */
    IN_APP_PAGER_SUMMARY("in_app_pager_summary"),

    /**
     * IAX page swipe
     */
    IN_APP_PAGE_SWIPE("in_app_page_swipe"),

    /**
     * IAX page view
     */
    IN_APP_PAGE_VIEW("in_app_page_view"),

    /**
     * IAX page action
     */
    IN_APP_PAGE_ACTION("in_app_page_action");

    internal companion object {

        @Throws(JsonException::class)
        fun from(value: String): EventAutomationTriggerType? {
            return entries.firstOrNull { it.value == value }
        }
    }

    override fun toJsonValue(): JsonValue = JsonValue.wrap(value)
}

/**
 * Compound automation trigger types.
 */
public enum class CompoundAutomationTriggerType(internal val value: String) : JsonSerializable {
    OR("or"),
    AND("and"),
    CHAIN("chain");

    internal companion object {

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): CompoundAutomationTriggerType? {
            val content = value.optString()
            return entries.firstOrNull { it.value == content }
        }
    }

    override fun toJsonValue(): JsonValue = JsonValue.wrap(value)
}

/**
 * Automation trigger
 */
public sealed class AutomationTrigger(
    public val id: String,
    public val goal: Double,
    public val type: String
) : JsonSerializable {

    /**
     * Event trigger
     */
    public class Event(internal val trigger: EventAutomationTrigger) :
        AutomationTrigger(trigger.id, trigger.goal, trigger.type.value) {

        override fun toJsonValue(): JsonValue = trigger.toJsonValue()
    }

    /**
     * Compound trigger
     */
    public class Compound(internal val trigger: CompoundAutomationTrigger) :
        AutomationTrigger(trigger.id, trigger.goal, trigger.type.value) {

        override fun toJsonValue(): JsonValue = trigger.toJsonValue()
    }

    internal fun matchEvent(
        event: AutomationEvent,
        data: TriggerData,
        resetOnTrigger: Boolean
    ): MatchResult? {
        val result = when(this) {
            is Compound -> trigger.matchEvent(event, data)
            is Event -> trigger.matchEvent(event, data)
        }

        if (resetOnTrigger && result?.isTriggered == true) {
            data.resetCounter()
        }

        return result
    }

    internal fun isTriggered(data: TriggerData): Boolean {
        return data.count >= this.goal
    }

    internal fun removeStaleChildData(data: TriggerData) {
        when(this) {
            is Compound -> trigger.removeStaleChildData(data)
            is Event -> {}
        }
    }


    internal companion object {
        private const val KEY_TYPE = "type"

        @Throws(JsonException::class)
        fun fromJson(
            value: JsonValue,
            executionType: TriggerExecutionType
        ): AutomationTrigger {

            val type = CompoundAutomationTriggerType.fromJson(value.requireMap().require(KEY_TYPE))

            return if (type != null) {
                Compound(CompoundAutomationTrigger.fromJson(value, executionType))
            } else {
                Event(EventAutomationTrigger.fromJson(value, executionType))
            }
        }

        fun activeSession(count: UInt): AutomationTrigger {
            return Event(
                EventAutomationTrigger(
                    type = EventAutomationTriggerType.ACTIVE_SESSION,
                    goal = count.toDouble()
                )
            )
        }

        fun foreground(count: UInt): AutomationTrigger {
            return Event(
                EventAutomationTrigger(
                    type = EventAutomationTriggerType.FOREGROUND,
                    goal = count.toDouble()
                )
            )
        }

        internal fun generateStableId(type: String, goal: Double, predicate: JsonPredicate? = null, executionType: TriggerExecutionType): String {
            val components = mutableListOf(type, goal.toString(), executionType.value)
            predicate?.let {
                components.add(it.toJsonValue().toString(true))
            }

            return UAStringUtil.sha256(components.joinToString(":"))
                ?: throw RuntimeException("failed to generate sha256 hash")
        }
    }

    override fun hashCode(): Int {
        return Objects.hash(id, goal, type)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AutomationTrigger

        if (id != other.id) return false
        if (goal != other.goal) return false
        if (type != other.type) return false

        return true
    }
}

/**
 * Event automation trigger
 */
public class EventAutomationTrigger internal constructor(
    public val id: String,
    public val type: EventAutomationTriggerType,
    public var goal: Double,
    public val predicate: JsonPredicate?
) : JsonSerializable {

    public constructor(
        type: EventAutomationTriggerType,
        goal: Double,
        predicate: JsonPredicate? = null
    ) : this (
        id = UUID.randomUUID().toString(),
        type = type,
        goal = goal,
        predicate = predicate,
    )

    internal companion object {
        private const val KEY_ID = "id"
        private const val KEY_TYPE = "type"
        private const val KEY_GOAL = "goal"
        private const val KEY_PREDICATE = "predicate"

        @Throws(JsonException::class)
        fun fromJson(
            value: JsonValue,
            executionType: TriggerExecutionType
        ): EventAutomationTrigger {
            val json = value.requireMap()
            val type = EventAutomationTriggerType.from(json.requireField(KEY_TYPE))
                ?: throw JsonException("invalid compound trigger type $json")

            val goal = json.requireField<Double>(KEY_GOAL)
            val predicate =  json.get(KEY_PREDICATE)?.let(JsonPredicate::parse)

            return EventAutomationTrigger(
                id = json.optionalField(KEY_ID) ?: AutomationTrigger.generateStableId(type.value, goal, predicate, executionType),
                type = type,
                goal = goal,
                predicate = predicate
            )
        }
    }

    internal fun matchEvent(event: AutomationEvent, data: TriggerData): MatchResult? {
        return when (event) {
            is AutomationEvent.StateChanged -> {
                stateTriggerMatch(event.state, data)
            }
            is AutomationEvent.Event -> {
                if (event.triggerType != this.type) {
                    return null
                }

                if (!isPredicatedMatching(event.data ?: JsonValue.NULL)) {
                    return null
                }
                evaluateResults(data, event.value)
            }
        }
    }

    private fun stateTriggerMatch(state: TriggerableState, data: TriggerData): MatchResult? {
        return when(this.type) {
            EventAutomationTriggerType.VERSION -> {
                val updatedVersion = state.versionUpdated ?: return null
                if (updatedVersion == data.lastTriggerableState?.versionUpdated) {
                    return null
                }

                if (!isPredicatedMatching(VersionUtils.createVersionObject(updatedVersion.toLong()))) {
                    return null
                }

                data.lastTriggerableState = state
                evaluateResults(data, 1.0)
            }
            EventAutomationTriggerType.ACTIVE_SESSION -> {
                val session = state.appSessionID ?: return null
                if (session == data.lastTriggerableState?.appSessionID) {
                    return null
                }

                data.lastTriggerableState = state
                evaluateResults(data, 1.0)
            }
            else -> null
        }
    }

    private fun isPredicatedMatching(value: JsonSerializable): Boolean {
        return this.predicate?.apply(value) ?: true
    }

    private fun evaluateResults(data: TriggerData, increment: Double): MatchResult {
        data.incrementCount(increment)
        return MatchResult(this.id, data.count >= this.goal)
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        KEY_ID to id,
        KEY_TYPE to type,
        KEY_GOAL to goal,
        KEY_PREDICATE to predicate
    ).toJsonValue()
}

public class CompoundAutomationTrigger internal constructor(
    public val id: String,
    public val type: CompoundAutomationTriggerType,
    public val goal: Double,
    internal val children: List<Child>
) : JsonSerializable {
    internal data class Child(
        val trigger: AutomationTrigger,
        val isSticky: Boolean?,
        val resetOnIncrement: Boolean?
    ) : JsonSerializable {
        companion object {
            private const val KEY_TRIGGER = "trigger"
            private const val KEY_IS_STICKY = "is_sticky"
            private const val KEY_RESET_ON_INCREMENT = "reset_on_increment"

            @Throws(JsonException::class)
            fun fromJson(
                value: JsonValue,
                executionType: TriggerExecutionType
            ): Child {
                val json = value.requireMap()
                return Child(
                    trigger = AutomationTrigger.fromJson(json.require(KEY_TRIGGER), executionType),
                    isSticky = json.optionalField(KEY_IS_STICKY),
                    resetOnIncrement = json.optionalField(KEY_RESET_ON_INCREMENT)
                )
            }
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            KEY_TRIGGER to trigger,
            KEY_IS_STICKY to isSticky,
            KEY_RESET_ON_INCREMENT to resetOnIncrement
        ).toJsonValue()
    }

    internal fun matchEvent(event: AutomationEvent, data: TriggerData): MatchResult {
        val triggeredChildren = triggeredChildrenCount(data)

        var childResults = matchChildren(event, data)

        //resend state event if children is triggered for chain triggers
        val state = data.lastTriggerableState
        if (this.type == CompoundAutomationTriggerType.CHAIN &&
            state != null && !event.isStateEvent &&
            triggeredChildren != triggeredChildrenCount(data)) {

            childResults = matchChildren(AutomationEvent.StateChanged(state), data)
        } else if (event is AutomationEvent.StateChanged) {
            data.lastTriggerableState = event.state
        }

        when(this.type) {
            CompoundAutomationTriggerType.AND, CompoundAutomationTriggerType.CHAIN -> {
                val shouldIncrement = childResults.all { it.isTriggered }

                if (shouldIncrement) {
                    children.forEach { child ->
                        if (child.isSticky != true) {
                            data.childDate(child.trigger.id).resetCounter()
                        }
                    }
                    data.incrementCount(1.0)
                }
            }

            CompoundAutomationTriggerType.OR -> {
                val shouldIncrement = childResults.any { it.isTriggered }
                if (shouldIncrement) {
                    children.forEach { child ->
                        val childData = data.childDate(child.trigger.id)

                        // Reset the child if it reached the goal or if we are resetting it
                        // on increment
                        if (childData.count >= child.trigger.goal || child.resetOnIncrement == true) {
                            childData.resetCounter()
                        }
                    }
                    data.incrementCount(1.0)
                }
            }
        }

        return MatchResult(triggerId = this.id, isTriggered = data.count >= goal)
    }

    private fun matchChildren(event: AutomationEvent, data: TriggerData): List<MatchResult> {
        var evaluateRemaining = true

        return children.map { child ->
            val childData = data.childDate(child.trigger.id)

            var matchResult: MatchResult? = null
            if (evaluateRemaining) {
                // Match the child without resetting it on trigger. We will process resets
                // after we get all the child results
                matchResult = child.trigger.matchEvent(event, childData, false)
            }

            val result = matchResult
                ?: MatchResult(
                    triggerId = child.trigger.id,
                    isTriggered = child.trigger.isTriggered(childData))

            if (this.type == CompoundAutomationTriggerType.CHAIN && evaluateRemaining && !result.isTriggered) {
                evaluateRemaining = false
            }

            result
        }
    }

    private fun triggeredChildrenCount(data: TriggerData): Int {
        return children.filter { child ->
            val state = data.children[child.trigger.id] ?: return@filter false
            return@filter child.trigger.isTriggered(state)
        }.size
    }

    internal companion object {
        private const val KEY_ID = "id"
        private const val KEY_TYPE = "type"
        private const val KEY_GOAL = "goal"
        private const val KEY_CHILDREN = "children"

        @Throws(JsonException::class)
        fun fromJson(
            value: JsonValue,
            executionType: TriggerExecutionType
        ): CompoundAutomationTrigger {
            val json = value.requireMap()

            val type = CompoundAutomationTriggerType.fromJson(json.require(KEY_TYPE))
                ?: throw JsonException("invalid compound trigger type $json")

            val goal = json.requireField<Double>(KEY_GOAL)


            return CompoundAutomationTrigger(
                id = json.optionalField(KEY_ID) ?: AutomationTrigger.generateStableId(type.value, goal, null, executionType),
                type = type,
                goal = goal,
                children = json.require(KEY_CHILDREN).requireList().map { Child.fromJson(it, executionType) }
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        KEY_ID to id,
        KEY_TYPE to type,
        KEY_GOAL to goal,
        KEY_CHILDREN to children
    ).toJsonValue()

    internal fun removeStaleChildData(data: TriggerData) {
        if (children.isEmpty()) {
            return
        }

        data.resetChildrenData()

        children.forEach {
            val childData = data.childDate(it.trigger.id)
            it.trigger.removeStaleChildData(childData)
        }
    }
}
